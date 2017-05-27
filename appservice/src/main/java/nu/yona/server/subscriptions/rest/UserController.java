/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.DOSProtectionService;
import nu.yona.server.analysis.rest.AppActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.exceptions.ConfirmationException;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.ErrorResponseDto;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.rest.StandardResourcesController;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.VPNProfileDto;
import nu.yona.server.util.ThymeleafUtil;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/users", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserController
{
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
	@Qualifier("appleMobileConfigTemplateEngine")
	private TemplateEngine templateEngine;

	@Autowired
	private AppleMobileConfigSigner appleMobileConfigSigner;

	@Autowired
	@Qualifier("sslRootCertificate")
	private X509Certificate sslRootCertificate;

	@RequestMapping(value = "/{userId}", params = { "includePrivateData" }, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr,
			@RequestParam(value = "includePrivateData", defaultValue = "false") String includePrivateDataStr,
			@PathVariable UUID userId)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		Optional<String> passwordToUse = getPasswordToUse(password, tempPassword);
		boolean includePrivateData = Boolean.TRUE.toString().equals(includePrivateDataStr);
		if (includePrivateData)
		{
			try (CryptoSession cryptoSession = CryptoSession.start(passwordToUse, () -> userService.canAccessPrivateData(userId)))
			{
				return createOkResponse(userService.getPrivateUser(userId, tempPassword.isPresent()), includePrivateData);
			}
		}
		else
		{
			return getPublicUser(passwordToUse, userId);
		}
	}

	@RequestMapping(value = "/{userId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getPublicUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		return createOkResponse(userService.getPublicUser(userId), false);
	}

	@RequestMapping(value = "/{userId}/apple.mobileconfig", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> getAppleMobileConfig(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId)
	{
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "x-apple-aspen-config"));
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(getUserSpecificAppleMobileConfig(userService.getPrivateUser(userId)), headers,
					HttpStatus.OK);
		}
	}

	private byte[] getUserSpecificAppleMobileConfig(UserDto privateUser)
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("ldapUsername", privateUser.getPrivateData().getVpnProfile().getVpnLoginId().toString());
		ctx.setVariable("ldapPassword", privateUser.getPrivateData().getVpnProfile().getVpnPassword());

		return signIfEnabled(templateEngine.process("apple.mobileconfig", ctx).getBytes(StandardCharsets.UTF_8));
	}

	private byte[] signIfEnabled(byte[] unsignedMobileconfig)
	{
		if (yonaProperties.getAppleMobileConfig().isSigningEnabled())
		{
			return appleMobileConfigSigner.sign(unsignedMobileconfig);
		}
		return unsignedMobileconfig;
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public HttpEntity<UserResource> addUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "overwriteUserConfirmationCode", required = false) String overwriteUserConfirmationCode,
			@RequestBody UserDto user, HttpServletRequest request)
	{
		// use DOS protection to prevent overwrite user confirmation code brute forcing,
		// and to prevent enumeration of all occupied mobile numbers
		return dosProtectionService.executeAttempt(getAddUserLinkBuilder().toUri(), request,
				yonaProperties.getSecurity().getMaxCreateUserAttemptsPerTimeWindow(),
				() -> addUser(password, Optional.ofNullable(overwriteUserConfirmationCode), user));
	}

	@RequestMapping(value = "/{userId}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<UserResource> updateUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr, @PathVariable UUID userId,
			@RequestBody UserDto userResource, HttpServletRequest request)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		if (tempPassword.isPresent())
		{
			return updateUserCreatedOnBuddyRequest(password, userId, userResource, tempPassword.get());
		}
		else
		{
			return updateUser(password, userId, userResource, request);
		}
	}

	private HttpEntity<UserResource> updateUser(Optional<String> password, UUID userId, UserDto userResource,
			HttpServletRequest request)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			// use DOS protection to prevent enumeration of all occupied mobile numbers
			return dosProtectionService.executeAttempt(getUpdateUserLinkBuilder(userId).toUri(), request,
					yonaProperties.getSecurity().getMaxUpdateUserAttemptsPerTimeWindow(), () -> updateUser(userId, userResource));
		}
	}

	private HttpEntity<UserResource> updateUserCreatedOnBuddyRequest(Optional<String> password, UUID userId, UserDto userResource,
			String tempPassword)
	{
		if (password.isPresent())
		{
			throw InvalidDataException.appProvidedPasswordNotSupported();
		}
		try (CryptoSession cryptoSession = CryptoSession.start(SecretKeyUtil.generateRandomSecretKey()))
		{
			return createOkResponse(userService.updateUserCreatedOnBuddyRequest(userId, tempPassword, userResource), true);
		}
	}

	private HttpEntity<UserResource> updateUser(UUID userId, UserDto userResource)
	{
		return createOkResponse(userService.updateUser(userId, userResource), true);
	}

	@RequestMapping(value = "/{userId}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void deleteUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			userService.deleteUser(userId, Optional.ofNullable(messageStr));
		}
	}

	@RequestMapping(value = "/{userId}/confirmMobileNumber", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<UserResource> confirmMobileNumber(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@RequestBody ConfirmationCodeDto mobileNumberConfirmation)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return createOkResponse(userService.confirmMobileNumber(userId, mobileNumberConfirmation.getCode()), true);
		}
	}

	@RequestMapping(value = "/{userId}/resendMobileNumberConfirmationCode", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> resendMobileNumberConfirmationCode(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			userService.resendMobileNumberConfirmationCode(userId);
			return new ResponseEntity<>(HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/{userId}/openApp", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> postOpenAppEvent(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			userService.postOpenAppEvent(userId);
			return new ResponseEntity<>(HttpStatus.OK);
		}
	}

	@ExceptionHandler(ConfirmationException.class)
	private ResponseEntity<ErrorResponseDto> handleException(ConfirmationException e, HttpServletRequest request)
	{
		if (e.getRemainingAttempts() >= 0)
		{
			ErrorResponseDto responseMessage = new ConfirmationFailedResponseDto(e.getMessageId(), e.getMessage(),
					e.getRemainingAttempts());
			logger.error("Confirmation failed", e);
			return new ResponseEntity<>(responseMessage, e.getStatusCode());
		}
		return globalExceptionMapping.handleYonaException(e, request);
	}

	static ControllerLinkBuilder getAddUserLinkBuilder()
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.addUser(Optional.empty(), null, null, null));
	}

	static ControllerLinkBuilder getUpdateUserLinkBuilder(UUID userId)
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.updateUser(Optional.empty(), null, userId, null, null));
	}

	static ControllerLinkBuilder getConfirmMobileNumberLinkBuilder(UUID userId)
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.confirmMobileNumber(Optional.empty(), userId, null));
	}

	private HttpEntity<UserResource> addUser(Optional<String> password, Optional<String> overwriteUserConfirmationCode,
			UserDto user)
	{
		checkAppProvidedPassword(password);
		SecretKey secretKey = password.map(p -> CryptoSession.getSecretKey(password.get()))
				.orElse(SecretKeyUtil.generateRandomSecretKey());
		try (CryptoSession cryptoSession = CryptoSession.start(secretKey))
		{
			return createResponse(userService.addUser(user, overwriteUserConfirmationCode), true, HttpStatus.CREATED);
		}
	}

	private void checkAppProvidedPassword(Optional<String> password)
	{
		if (password.isPresent())
		{
			if (yonaProperties.getSecurity().isAppProvidedPasswordEnabled())
			{
				logger.warn("Creating user with app-provided password");
			}
			else
			{
				throw InvalidDataException.appProvidedPasswordNotSupported();
			}
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

	private HttpEntity<UserResource> createResponse(UserDto user, boolean includePrivateData, HttpStatus status)
	{
		return new ResponseEntity<>(
				new UserResourceAssembler(curieProvider, pinResetRequestController, includePrivateData).toResource(user), status);
	}

	private HttpEntity<UserResource> createOkResponse(UserDto user, boolean includePrivateData)
	{
		return createResponse(user, includePrivateData, HttpStatus.OK);
	}

	static Link getUserSelfLinkWithTempPassword(UUID userId, String tempPassword)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).updateUser(Optional.empty(), tempPassword, userId, null, null));
		return linkBuilder.withSelfRel();
	}

	private static Link getConfirmMobileLink(UUID userId)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).confirmMobileNumber(Optional.empty(), userId, null));
		return linkBuilder.withRel("confirmMobileNumber");
	}

	public static Link getResendMobileNumberConfirmationLink(UUID userId)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).resendMobileNumberConfirmationCode(Optional.empty(), userId));
		return linkBuilder.withRel("resendMobileNumberConfirmationCode");
	}

	public static Link getPostOpenAppEventLink(UUID userId)
	{
		ControllerLinkBuilder linkBuilder = linkTo(methodOn(UserController.class).postOpenAppEvent(Optional.empty(), userId));
		return linkBuilder.withRel("postOpenAppEvent");
	}

	private static Link getUserSelfLink(UUID userId, boolean includePrivateData)
	{
		ControllerLinkBuilder linkBuilder;
		if (includePrivateData)
		{
			linkBuilder = linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, Boolean.TRUE.toString(), userId));
		}
		else
		{
			linkBuilder = linkTo(methodOn(UserController.class).getPublicUser(Optional.empty(), userId));
		}
		return linkBuilder.withSelfRel();
	}

	public static Link getPublicUserLink(String rel, UUID userId)
	{
		return linkTo(methodOn(UserController.class).getPublicUser(Optional.empty(), userId)).withRel(rel);
	}

	public static Link getPrivateUserLink(String rel, UUID userId)
	{
		return linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, Boolean.TRUE.toString(), userId))
				.withRel(rel);
	}

	@PostConstruct
	private void setSslRootCertificateCn()
	{
		try
		{
			LdapName name = new LdapName(sslRootCertificate.getIssuerX500Principal().getName());
			UserResource.setSslRootCertificateCn(name.getRdn(0).getValue().toString());
		}
		catch (InvalidNameException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	static class UserResource extends Resource<UserDto>
	{
		private final CurieProvider curieProvider;
		private static String sslRootCertificateCn;

		public UserResource(CurieProvider curieProvider, UserDto user)
		{
			super(user);
			this.curieProvider = curieProvider;
		}

		public static void setSslRootCertificateCn(String sslRootCertificateCn)
		{
			UserResource.sslRootCertificateCn = sslRootCertificateCn;
		}

		@JsonProperty("sslRootCertCN")
		@JsonInclude(Include.NON_EMPTY)
		public Optional<String> getSslRootCertCn()
		{
			if (!includeLinksAndEmbeddedData())
			{
				return Optional.empty();
			}

			return Optional.of(sslRootCertificateCn);
		}

		@JsonProperty("_embedded")
		@JsonInclude(Include.NON_EMPTY)
		public Map<String, Object> getEmbeddedResources()
		{
			if (!includeLinksAndEmbeddedData())
			{
				return Collections.emptyMap();
			}

			Set<BuddyDto> buddies = getContent().getPrivateData().getBuddies();
			HashMap<String, Object> result = new HashMap<>();
			result.put(curieProvider.getNamespacedRelFor(UserDto.BUDDIES_REL_NAME),
					BuddyController.createAllBuddiesCollectionResource(curieProvider, getContent().getId(), buddies));

			Set<GoalDto> goals = getContent().getPrivateData().getGoals();
			result.put(curieProvider.getNamespacedRelFor(UserDto.GOALS_REL_NAME),
					GoalController.createAllGoalsCollectionResource(getContent().getId(), goals));

			return result;
		}

		private boolean includeLinksAndEmbeddedData()
		{
			return (getContent().getPrivateData() != null) && getContent().isMobileNumberConfirmed();
		}

		@JsonInclude(Include.NON_EMPTY)
		public Resource<VPNProfileDto> getVpnProfile()
		{
			if (!includeLinksAndEmbeddedData())
			{
				return null;
			}
			Resource<VPNProfileDto> vpnProfileResource = new Resource<>(getContent().getPrivateData().getVpnProfile());
			addOvpnProfileLink(vpnProfileResource);
			return vpnProfileResource;
		}

		private void addOvpnProfileLink(Resource<VPNProfileDto> vpnProfileResource)
		{
			vpnProfileResource.add(
					new Link(ServletUriComponentsBuilder.fromCurrentContextPath().path("/vpn/profile.ovpn").build().toUriString(),
							"ovpnProfile"));
		}

		static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserId)
		{
			BuddyController methodOn = methodOn(BuddyController.class);
			return linkTo(methodOn.getAllBuddies(null, requestingUserId));
		}
	}

	public static class UserResourceAssembler extends ResourceAssemblerSupport<UserDto, UserResource>
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
		public UserResource toResource(UserDto user)
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
					addPostOpenAppEventLink(userResource);
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
					methodOn(UserController.class).getAppleMobileConfig(Optional.empty(), userResource.getContent().getId()))
							.withRel("appleMobileConfig"));
		}

		private void addSslRootCertificateLink(Resource<UserDto> userResource)
		{
			userResource.add(linkTo(methodOn(StandardResourcesController.class).getSslRootCert()).withRel("sslRootCert"));
		}

		@Override
		protected UserResource instantiateResource(UserDto user)
		{
			return new UserResource(curieProvider, user);
		}

		private static void addSelfLink(Resource<UserDto> userResource, boolean includePrivateData)
		{
			if (userResource.getContent().getId() == null)
			{
				// removed user
				return;
			}

			userResource.add(UserController.getUserSelfLink(userResource.getContent().getId(), includePrivateData));
		}

		private static void addEditLink(Resource<UserDto> userResource)
		{
			userResource.add(linkTo(methodOn(UserController.class).updateUser(Optional.empty(), null,
					userResource.getContent().getId(), null, null)).withRel(JsonRootRelProvider.EDIT_REL));
		}

		private static void addConfirmMobileNumberLink(Resource<UserDto> userResource)
		{
			userResource.add(UserController.getConfirmMobileLink(userResource.getContent().getId()));
		}

		private static void addResendMobileNumberConfirmationLink(Resource<UserDto> userResource)
		{
			userResource.add(UserController.getResendMobileNumberConfirmationLink(userResource.getContent().getId()));
		}

		private static void addPostOpenAppEventLink(Resource<UserDto> userResource)
		{
			userResource.add(UserController.getPostOpenAppEventLink(userResource.getContent().getId()));
		}

		private void addWeekActivityOverviewsLink(UserResource userResource)
		{
			userResource.add(UserActivityController.getUserWeekActivityOverviewsLinkBuilder(userResource.getContent().getId())
					.withRel(UserActivityController.WEEK_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsLink(UserResource userResource)
		{
			userResource.add(UserActivityController.getUserDayActivityOverviewsLinkBuilder(userResource.getContent().getId())
					.withRel(UserActivityController.DAY_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsWithBuddiesLink(UserResource userResource)
		{
			userResource
					.add(UserActivityController.getDayActivityOverviewsWithBuddiesLinkBuilder(userResource.getContent().getId())
							.withRel("dailyActivityReportsWithBuddies"));
		}

		private void addMessagesLink(UserResource userResource)
		{
			userResource.add(MessageController.getMessagesLink(userResource.getContent().getId()));
		}

		private void addNewDeviceRequestLink(UserResource userResource)
		{
			userResource.add(NewDeviceRequestController
					.getNewDeviceRequestLinkBuilder(userResource.getContent().getMobileNumber()).withRel("newDeviceRequest"));
		}

		private void addAppActivityLink(UserResource userResource)
		{
			userResource.add(AppActivityController.getAppActivityLink(userResource.getContent().getId()));
		}
	}
}
