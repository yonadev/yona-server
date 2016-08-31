/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.servlet.http.HttpServletRequest;

import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.velocity.VelocityEngineUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.DOSProtectionService;
import nu.yona.server.analysis.rest.AppActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.ConfirmationException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.ErrorResponseDTO;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDTO;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.VPNProfileDTO;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/users", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserController
{
	private static final String SSL_ROOT_CERTIFICATE_PATH = "/ssl/rootcert.cer";

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private DOSProtectionService dosProtectionService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private CurieProvider curieProvider;

	@Autowired
	private GlobalExceptionMapping globalExceptionMapping;

	@Autowired
	private PinResetRequestController pinResetRequestController;

	@Autowired
	private VelocityEngine velocityEngine;

	@RequestMapping(value = "/{id}", params = { "includePrivateData" }, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr,
			@RequestParam(value = "includePrivateData", defaultValue = "false") String includePrivateDataStr,
			@PathVariable UUID id)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		Optional<String> passwordToUse = getPasswordToUse(password, tempPassword);
		boolean includePrivateData = Boolean.TRUE.toString().equals(includePrivateDataStr);
		if (includePrivateData)
		{
			return CryptoSession.execute(passwordToUse, () -> userService.canAccessPrivateData(id),
					() -> createOKResponse(userService.getPrivateUser(id), includePrivateData));
		}
		else
		{
			return getPublicUser(passwordToUse, id);
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getPublicUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID id)
	{
		return createOKResponse(userService.getPublicUser(id), false);
	}

	@RequestMapping(value = "/{id}/apple.mobileconfig", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> getAppleMobileConfig(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id)
	{
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "x-apple-aspen-config"));
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(id),
				() -> new ResponseEntity<byte[]>(getUserSpecificAppleMobileConfig(userService.getPrivateUser(id)), headers,
						HttpStatus.OK));
	}

	private byte[] getUserSpecificAppleMobileConfig(UserDTO privateUser)
	{
		Map<String, Object> templateParameters = new HashMap<String, Object>();
		templateParameters.put("ldapUsername", privateUser.getPrivateData().getVpnProfile().getVpnLoginID().toString());
		templateParameters.put("ldapPassword", privateUser.getPrivateData().getVpnProfile().getVpnPassword());
		return VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "apple.mobileconfig.vm", "UTF-8", templateParameters)
				.getBytes(StandardCharsets.UTF_8);
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public HttpEntity<UserResource> addUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "overwriteUserConfirmationCode", required = false) String overwriteUserConfirmationCode,
			@RequestBody UserDTO user, HttpServletRequest request)
	{
		return dosProtectionService.executeAttempt(getAddUserLinkBuilder().toUri(), request,
				yonaProperties.getSecurity().getMaxCreateUserAttemptsPerTimeWindow(),
				() -> addUser(password, Optional.ofNullable(overwriteUserConfirmationCode), user));
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<UserResource> updateUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr, @PathVariable UUID id,
			@RequestBody UserDTO userResource)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		if (tempPassword.isPresent())
		{
			return CryptoSession.execute(password, null,
					() -> createOKResponse(userService.updateUserCreatedOnBuddyRequest(id, tempPassword.get(), userResource),
							true));
		}
		else
		{
			return CryptoSession.execute(password, () -> userService.canAccessPrivateData(id),
					() -> createOKResponse(userService.updateUser(id, userResource), true));
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void deleteUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			userService.deleteUser(id, Optional.ofNullable(messageStr));
			return null;
		});
	}

	@RequestMapping(value = "/{id}/confirmMobileNumber", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<UserResource> confirmMobileNumber(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id,
			@RequestBody ConfirmationCodeDTO mobileNumberConfirmation)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(id),
				() -> createOKResponse(userService.confirmMobileNumber(id, mobileNumberConfirmation.getCode()), true));
	}

	@RequestMapping(value = "/{id}/resendMobileNumberConfirmationCode", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> resendMobileNumberConfirmationCode(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id),
				() -> userService.resendMobileNumberConfirmationCode(id));
		return new ResponseEntity<Void>(HttpStatus.OK);
	}

	@ExceptionHandler(ConfirmationException.class)
	private ResponseEntity<ErrorResponseDTO> handleException(ConfirmationException e)
	{
		if (e.getRemainingAttempts() >= 0)
		{
			ErrorResponseDTO responseMessage = new ConfirmationFailedResponseDTO(e.getMessageId(), e.getMessage(),
					e.getRemainingAttempts());
			logger.error("Confirmation failed", e);
			return new ResponseEntity<ErrorResponseDTO>(responseMessage, e.getStatusCode());
		}
		return globalExceptionMapping.handleYonaException(e);
	}

	static ControllerLinkBuilder getAddUserLinkBuilder()
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.addUser(Optional.empty(), null, null, null));
	}

	static ControllerLinkBuilder getConfirmMobileNumberLinkBuilder(UUID userID)
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.confirmMobileNumber(Optional.empty(), userID, null));
	}

	private HttpEntity<UserResource> addUser(Optional<String> password, Optional<String> overwriteUserConfirmationCode,
			UserDTO user)
	{
		if (overwriteUserConfirmationCode.isPresent())
		{
			return CryptoSession.execute(password,
					() -> createResponse(userService.addUser(user, overwriteUserConfirmationCode), true, HttpStatus.CREATED));
		}
		else
		{
			return CryptoSession.execute(password,
					() -> createResponse(userService.addUser(user, Optional.empty()), true, HttpStatus.CREATED));
		}
	}

	private Optional<String> getPasswordToUse(Optional<String> password, Optional<String> tempPassword)
	{
		if (password.isPresent())
		{
			return password;
		}
		if (tempPassword.isPresent())
		{
			return tempPassword;
		}
		return Optional.empty();
	}

	private HttpEntity<UserResource> createResponse(UserDTO user, boolean includePrivateData, HttpStatus status)
	{
		return new ResponseEntity<UserResource>(
				new UserResourceAssembler(curieProvider, pinResetRequestController, includePrivateData).toResource(user), status);
	}

	private HttpEntity<UserResource> createOKResponse(UserDTO user, boolean includePrivateData)
	{
		return createResponse(user, includePrivateData, HttpStatus.OK);
	}

	static Link getUserSelfLinkWithTempPassword(UUID userID, String tempPassword)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).updateUser(Optional.empty(), tempPassword, userID, null));
		return linkBuilder.withSelfRel();
	}

	private static Link getConfirmMobileLink(UUID userID)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).confirmMobileNumber(Optional.empty(), userID, null));
		return linkBuilder.withRel("confirmMobileNumber");
	}

	public static Link getResendMobileNumberConfirmationLink(UUID userID)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).resendMobileNumberConfirmationCode(Optional.empty(), userID));
		return linkBuilder.withRel("resendMobileNumberConfirmationCode");
	}

	private static Link getUserSelfLink(UUID userID, boolean includePrivateData)
	{
		ControllerLinkBuilder linkBuilder;
		if (includePrivateData)
		{
			linkBuilder = linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, Boolean.TRUE.toString(), userID));
		}
		else
		{
			linkBuilder = linkTo(methodOn(UserController.class).getPublicUser(Optional.empty(), userID));
		}
		return linkBuilder.withSelfRel();
	}

	public static Link getPublicUserLink(String rel, UUID userID)
	{
		return linkTo(methodOn(UserController.class).getPublicUser(Optional.empty(), userID)).withRel(rel);
	}

	public static Link getPrivateUserLink(String rel, UUID userID)
	{
		return linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, Boolean.TRUE.toString(), userID))
				.withRel(rel);
	}

	static class UserResource extends Resource<UserDTO>
	{
		private final CurieProvider curieProvider;

		public UserResource(CurieProvider curieProvider, UserDTO user)
		{
			super(user);
			this.curieProvider = curieProvider;
		}

		@JsonProperty("sslRootCertCN")
		@JsonInclude(Include.NON_EMPTY)
		public String getSslRootCertCN()
		{
			if (!includeLinksAndEmbeddedData())
			{
				return null;
			}

			try (InputStream certInputStream = new ClassPathResource(Paths.get("static", SSL_ROOT_CERTIFICATE_PATH).toString())
					.getInputStream())
			{
				X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
						.generateCertificate(certInputStream);
				String dn = cert.getIssuerX500Principal().getName();
				LdapName ln = new LdapName(dn);
				return ln.getRdns().stream().filter(rdn -> rdn.getType().equalsIgnoreCase("CN")).findFirst().get().getValue()
						.toString();
			}
			catch (IOException e)
			{
				throw YonaException.unexpected(e);
			}
			catch (CertificateException e)
			{
				throw YonaException.unexpected(e);
			}
			catch (InvalidNameException e)
			{
				throw YonaException.unexpected(e);
			}
		}

		@JsonProperty("_embedded")
		@JsonInclude(Include.NON_EMPTY)
		public Map<String, Object> getEmbeddedResources()
		{
			if (!includeLinksAndEmbeddedData())
			{
				return Collections.emptyMap();
			}

			Set<BuddyDTO> buddies = getContent().getPrivateData().getBuddies();
			HashMap<String, Object> result = new HashMap<String, Object>();
			result.put(curieProvider.getNamespacedRelFor(UserDTO.BUDDIES_REL_NAME),
					BuddyController.createAllBuddiesCollectionResource(curieProvider, getContent().getID(), buddies));

			Set<GoalDTO> goals = getContent().getPrivateData().getGoals();
			result.put(curieProvider.getNamespacedRelFor(UserDTO.GOALS_REL_NAME),
					GoalController.createAllGoalsCollectionResource(getContent().getID(), goals));

			return result;
		}

		private boolean includeLinksAndEmbeddedData()
		{
			return (getContent().getPrivateData() != null) && getContent().isMobileNumberConfirmed();
		}

		@JsonInclude(Include.NON_EMPTY)
		public Resource<VPNProfileDTO> getVpnProfile()
		{
			if (!includeLinksAndEmbeddedData())
			{
				return null;
			}
			Resource<VPNProfileDTO> vpnProfileResource = new Resource<VPNProfileDTO>(
					getContent().getPrivateData().getVpnProfile());
			addOvpnProfileLink(vpnProfileResource);
			return vpnProfileResource;
		}

		private void addOvpnProfileLink(Resource<VPNProfileDTO> vpnProfileResource)
		{
			vpnProfileResource.add(
					new Link(ServletUriComponentsBuilder.fromCurrentContextPath().path("/vpn/profile.ovpn").build().toUriString(),
							"ovpnProfile"));
		}

		static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserID)
		{
			BuddyController methodOn = methodOn(BuddyController.class);
			return linkTo(methodOn.getAllBuddies(null, requestingUserID));
		}
	}

	public static class UserResourceAssembler extends ResourceAssemblerSupport<UserDTO, UserResource>
	{
		private final boolean includePrivateData;
		private final CurieProvider curieProvider;
		private final PinResetRequestController pinResetRequestController;

		public UserResourceAssembler(CurieProvider curieProvider, boolean includePrivateData)
		{
			this(curieProvider, null, includePrivateData);
		}

		public UserResourceAssembler(CurieProvider curieProvider, PinResetRequestController pinResetRequestController,
				boolean includePrivateData)
		{
			super(UserController.class, UserResource.class);
			this.curieProvider = curieProvider;
			this.pinResetRequestController = pinResetRequestController;
			this.includePrivateData = includePrivateData;
		}

		@Override
		public UserResource toResource(UserDTO user)
		{
			UserResource userResource = instantiateResource(user);
			addSelfLink(userResource, includePrivateData);
			if (includePrivateData && !user.isMobileNumberConfirmed())
			{
				// The mobile number is not yet confirmed, so we can add the link
				addConfirmMobileNumberLink(userResource);
				addResendMobileNumberConfirmationLink(userResource);
			}
			if (includePrivateData)
			{
				addEditLink(userResource);
				if (user.isMobileNumberConfirmed())
				{
					addMessagesLink(userResource);
					addDayActivityOverviewsLink(userResource);
					addWeekActivityOverviewsLink(userResource);
					addDayActivityOverviewsWithBuddiesLink(userResource);
					addNewDeviceRequestLink(userResource);
					addAppActivityLink(userResource);
					pinResetRequestController.addLinks(userResource);
					addSslRootCertificateLink(userResource);
					addAppleMobileConfigLink(userResource);
				}
			}
			return userResource;
		}

		private void addAppleMobileConfigLink(UserResource userResource)
		{
			userResource.add(linkTo(
					methodOn(UserController.class).getAppleMobileConfig(Optional.empty(), userResource.getContent().getID()))
							.withRel("appleMobileConfig"));
		}

		private void addSslRootCertificateLink(Resource<UserDTO> userResource)
		{
			userResource.add(new Link(
					ServletUriComponentsBuilder.fromCurrentContextPath().path(SSL_ROOT_CERTIFICATE_PATH).build().toUriString(),
					"sslRootCert"));
		}

		@Override
		protected UserResource instantiateResource(UserDTO user)
		{
			return new UserResource(curieProvider, user);
		}

		private static void addSelfLink(Resource<UserDTO> userResource, boolean includePrivateData)
		{
			if (userResource.getContent().getID() == null)
			{
				// removed user
				return;
			}

			userResource.add(UserController.getUserSelfLink(userResource.getContent().getID(), includePrivateData));
		}

		private static void addEditLink(Resource<UserDTO> userResource)
		{
			userResource.add(linkTo(
					methodOn(UserController.class).updateUser(Optional.empty(), null, userResource.getContent().getID(), null))
							.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private static void addConfirmMobileNumberLink(Resource<UserDTO> userResource)
		{
			userResource.add(UserController.getConfirmMobileLink(userResource.getContent().getID()));
		}

		private static void addResendMobileNumberConfirmationLink(Resource<UserDTO> userResource)
		{
			userResource.add(UserController.getResendMobileNumberConfirmationLink(userResource.getContent().getID()));
		}

		private void addWeekActivityOverviewsLink(UserResource userResource)
		{
			userResource.add(UserActivityController.getUserWeekActivityOverviewsLinkBuilder(userResource.getContent().getID())
					.withRel(UserActivityController.WEEK_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsLink(UserResource userResource)
		{
			userResource.add(UserActivityController.getUserDayActivityOverviewsLinkBuilder(userResource.getContent().getID())
					.withRel(UserActivityController.DAY_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsWithBuddiesLink(UserResource userResource)
		{
			userResource
					.add(UserActivityController.getDayActivityOverviewsWithBuddiesLinkBuilder(userResource.getContent().getID())
							.withRel("dailyActivityReportsWithBuddies"));
		}

		private void addMessagesLink(UserResource userResource)
		{
			userResource.add(MessageController.getMessagesLink(userResource.getContent().getID()));
		}

		private void addNewDeviceRequestLink(UserResource userResource)
		{
			userResource.add(NewDeviceRequestController
					.getNewDeviceRequestLinkBuilder(userResource.getContent().getMobileNumber()).withRel("newDeviceRequest"));
		}

		private void addAppActivityLink(UserResource userResource)
		{
			userResource.add(AppActivityController.getAppActivityLink(userResource.getContent().getID()));
		}
	}
}
