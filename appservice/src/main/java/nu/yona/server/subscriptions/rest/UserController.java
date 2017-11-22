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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.DOSProtectionService;
import nu.yona.server.Translator;
import nu.yona.server.analysis.rest.AppActivityController;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.rest.DeviceController;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.exceptions.ConfirmationException;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.ErrorResponseDto;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.rest.StandardResourcesController;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.BuddyUserPrivateDataDto;
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.VPNProfileDto;
import nu.yona.server.util.ThymeleafUtil;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/users", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserController extends ControllerBase
{
	private static final String REQUESTING_USER_ID_PARAM = "requestingUserId";
	private static final String INCLUDE_PRIVATE_DATA_PARAM = "includePrivateData";
	private static final String TEMP_PASSWORD_PARAM = "tempPassword";
	private static final Map<String, Object> OMITTED_PARAMS;
	static
	{
		Map<String, Object> v = new HashMap<>();
		v.put(TEMP_PASSWORD_PARAM, null);
		v.put(INCLUDE_PRIVATE_DATA_PARAM, null);
		OMITTED_PARAMS = Collections.unmodifiableMap(v);
	}

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private BuddyService buddyService;

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

	@Autowired
	private Translator translator;

	@RequestMapping(value = "/{userId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = TEMP_PASSWORD_PARAM, required = false) String tempPasswordStr,
			@RequestParam(value = REQUESTING_USER_ID_PARAM, required = false) String requestingUserIdStr,
			@RequestParam(value = INCLUDE_PRIVATE_DATA_PARAM, required = false, defaultValue = "false") String includePrivateDataStr,
			@PathVariable UUID userId)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		Optional<String> passwordToUse = getPasswordToUse(password, tempPassword);
		return Optional.ofNullable(determineRequestingUserId(userId, requestingUserIdStr, includePrivateDataStr))
				.map(UUID::fromString).map(ruid -> getUser(ruid, userId, tempPassword.isPresent(), passwordToUse))
				.orElseGet(() -> getPublicUser(userId));
	}

	/**
	 * This method contains a backward compatibility mechanism: In the past, users were using the "includePrivateData" query
	 * parameter to indicate they were fetching their own user entity including the private data. Nowadays, this is indicated by
	 * setting requestingUserId to the ID of the user being fetched.<br/>
	 * <br/>
	 * Given that the old URL with includePriateData is stored on the devices of the users, that query parameter needs to to be
	 * supported forever.
	 */
	private String determineRequestingUserId(UUID userId, String requestingUserIdStr, String includePrivateDataStr)
	{
		return Boolean.TRUE.toString().equals(includePrivateDataStr) ? userId.toString() : requestingUserIdStr;
	}

	private HttpEntity<UserResource> getUser(UUID requestingUserId, UUID userId, boolean isCreatedOnBuddyRequest,
			Optional<String> password)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.canAccessPrivateData(requestingUserId)))
		{
			return createOkResponse(requestingUserId.equals(userId) ? userService.getPrivateUser(userId, isCreatedOnBuddyRequest)
					: buddyService.getUserOfBuddy(requestingUserId, userId), createResourceAssembler(requestingUserId));
		}
	}

	private HttpEntity<UserResource> getPublicUser(UUID userId)
	{
		return createOkResponse(userService.getPublicUser(userId), createResourceAssembler());
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
		ctx.setVariable("ldapUsername", privateUser.getOwnPrivateData().getVpnProfile().getVpnLoginId().toString());
		ctx.setVariable("ldapPassword", privateUser.getOwnPrivateData().getVpnProfile().getVpnPassword());

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
	public HttpEntity<UserResource> addUser(
			@RequestParam(value = "overwriteUserConfirmationCode", required = false) String overwriteUserConfirmationCode,
			@RequestBody PostPutUserDto postPutUser, HttpServletRequest request)
	{
		UserDto user = convertToUser(postPutUser);
		// use DOS protection to prevent overwrite user confirmation code brute forcing,
		// and to prevent enumeration of all occupied mobile numbers
		return dosProtectionService.executeAttempt(getAddUserLinkBuilder().toUri(), request,
				yonaProperties.getSecurity().getMaxCreateUserAttemptsPerTimeWindow(),
				() -> addUser(Optional.ofNullable(overwriteUserConfirmationCode), user));
	}

	private UserDto convertToUser(PostPutUserDto postPutUser)
	{
		return UserDto.createInstance(postPutUser.firstName, postPutUser.lastName, postPutUser.mobileNumber, postPutUser.nickname,
				postPutUser.getDevice());
	}

	@RequestMapping(value = "/{userId}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<UserResource> updateUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = TEMP_PASSWORD_PARAM, required = false) String tempPasswordStr, @PathVariable UUID userId,
			@RequestBody PostPutUserDto postPutUser, HttpServletRequest request)
	{
		UserDto user = convertToUser(postPutUser);
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		if (tempPassword.isPresent())
		{
			return updateUserCreatedOnBuddyRequest(password, userId, user, tempPassword.get());
		}
		else
		{
			return updateUser(password, userId, user, request);
		}
	}

	private HttpEntity<UserResource> updateUser(Optional<String> password, UUID userId, UserDto user, HttpServletRequest request)
	{
		assert user.getOwnPrivateData().getDevices()
				.isEmpty() : "Embedding devices in an update request is only allowed when updating a user created on buddy request";
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			// use DOS protection to prevent enumeration of all occupied mobile numbers
			return dosProtectionService.executeAttempt(getUpdateUserLinkBuilder(userId).toUri(), request,
					yonaProperties.getSecurity().getMaxUpdateUserAttemptsPerTimeWindow(), () -> updateUser(userId, user));
		}
	}

	private HttpEntity<UserResource> updateUserCreatedOnBuddyRequest(Optional<String> password, UUID userId, UserDto user,
			String tempPassword)
	{
		if (password.isPresent())
		{
			throw InvalidDataException.appProvidedPasswordNotSupported();
		}
		addDefaultDeviceIfNotProvided(user);
		try (CryptoSession cryptoSession = CryptoSession.start(SecretKeyUtil.generateRandomSecretKey()))
		{
			return createOkResponse(userService.updateUserCreatedOnBuddyRequest(userId, tempPassword, user),
					createResourceAssembler(userId));
		}
	}

	private void addDefaultDeviceIfNotProvided(UserDto user)
	{
		Set<DeviceBaseDto> devices = user.getOwnPrivateData().getDevices();
		if (devices.isEmpty())
		{
			devices.add(new UserDeviceDto(translator.getLocalizedMessage("default.device.name"), OperatingSystem.UNKNOWN));
		}
	}

	private HttpEntity<UserResource> updateUser(UUID userId, UserDto user)
	{
		return createOkResponse(userService.updateUser(userId, user), createResourceAssembler(userId));
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
			return createOkResponse(userService.confirmMobileNumber(userId, mobileNumberConfirmation.getCode()),
					createResourceAssembler(userId));
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
			return createOkResponse();
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
			return createOkResponse();
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
		return linkTo(methodOn.addUser(null, null, null));
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

	private HttpEntity<UserResource> addUser(Optional<String> overwriteUserConfirmationCode, UserDto user)
	{
		addDefaultDeviceIfNotProvided(user);
		try (CryptoSession cryptoSession = CryptoSession.start(SecretKeyUtil.generateRandomSecretKey()))
		{
			UserDto createdUser = userService.addUser(user, overwriteUserConfirmationCode);
			return createResponse(createdUser, HttpStatus.CREATED, createResourceAssembler(createdUser.getId()));
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

	private UserResourceAssembler createResourceAssembler(UUID requestingUserId)
	{
		return new UserResourceAssembler(curieProvider, pinResetRequestController, requestingUserId);
	}

	private UserResourceAssembler createResourceAssembler()
	{
		return new UserResourceAssembler(curieProvider);
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

	private static Link getUserLink(String rel, UUID userId, Optional<UUID> requestingUserId)
	{
		String requestingUserIdStr = requestingUserId.map(UUID::toString).orElse(null);
		return linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, requestingUserIdStr, null, userId))
				.withRel(rel).expand(OMITTED_PARAMS);
	}

	public static Link getPublicUserLink(String rel, UUID userId)
	{
		return getUserLink(rel, userId, Optional.empty());
	}

	public static Link getPrivateUserLink(String rel, UUID userId)
	{
		return getUserLink(rel, userId, Optional.of(userId));
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

	static class PostPutUserDto
	{
		private final String firstName;
		private final String lastName;
		private final String mobileNumber;
		private final String nickname;
		private final String deviceName;
		private final String deviceOperatingSystem;

		@JsonCreator
		public PostPutUserDto(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
				@JsonProperty("mobileNumber") String mobileNumber, @JsonProperty("nickname") String nickname,
				@JsonProperty(value = "deviceName", required = false) String deviceName,
				@JsonProperty(value = "deviceOperatingSystem", required = false) String deviceOperatingSystem,
				@JsonProperty("_links") Object ignored1, @JsonProperty("yonaPassword") Object ignored2)
		{
			this.firstName = firstName;
			this.lastName = lastName;
			this.mobileNumber = mobileNumber;
			this.nickname = nickname;
			this.deviceName = deviceName;
			this.deviceOperatingSystem = deviceOperatingSystem;
		}

		Optional<UserDeviceDto> getDevice()
		{
			return (deviceName == null) ? Optional.empty()
					: Optional.of(UserDeviceDto.createPostPutInstance(deviceName, deviceOperatingSystem));
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
			if (!includeLinksAndEmbeddedData() || isForBuddy())
			{
				return Optional.empty();
			}

			return Optional.of(sslRootCertificateCn);
		}

		@JsonProperty("_embedded")
		@JsonInclude(Include.NON_EMPTY)
		public Map<String, Object> getEmbeddedResources()
		{
			if (!includeLinksAndEmbeddedData() || isForBuddy())
			{
				return Collections.emptyMap();
			}

			HashMap<String, Object> result = new HashMap<>();

			Set<BuddyDto> buddies = getContent().getOwnPrivateData().getBuddies();
			UUID userId = getContent().getId();
			result.put(curieProvider.getNamespacedRelFor(UserDto.BUDDIES_REL_NAME),
					BuddyController.createAllBuddiesCollectionResource(curieProvider, userId, buddies));

			Set<GoalDto> goals = getContent().getOwnPrivateData().getGoals();
			result.put(curieProvider.getNamespacedRelFor(UserDto.GOALS_REL_NAME),
					GoalController.createAllGoalsCollectionResource(userId, goals));

			Set<DeviceBaseDto> devices = getContent().getOwnPrivateData().getDevices();
			result.put(curieProvider.getNamespacedRelFor(UserDto.DEVICES_REL_NAME),
					DeviceController.createAllDevicesCollectionResource(curieProvider, userId, devices));

			return result;
		}

		private boolean includeLinksAndEmbeddedData()
		{
			return (getContent().getPrivateData() != null) && getContent().isMobileNumberConfirmed();
		}

		private boolean isForBuddy()
		{
			return (getContent().getPrivateData() instanceof BuddyUserPrivateDataDto);
		}

		@JsonInclude(Include.NON_EMPTY)
		public Resource<VPNProfileDto> getVpnProfile()
		{
			if (!includeLinksAndEmbeddedData() || isForBuddy())
			{
				return null;
			}
			Resource<VPNProfileDto> vpnProfileResource = new Resource<>(getContent().getOwnPrivateData().getVpnProfile());
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
		private final Optional<UUID> requestingUserId;
		private final CurieProvider curieProvider;
		private final Optional<PinResetRequestController> pinResetRequestController;
		private boolean includeAllLinks;

		public UserResourceAssembler(CurieProvider curieProvider)
		{
			this(false, curieProvider, Optional.empty(), Optional.empty());
		}

		public UserResourceAssembler(CurieProvider curieProvider, UUID requestingUserId)
		{
			this(false, curieProvider, Optional.empty(), Optional.of(requestingUserId));
		}

		public UserResourceAssembler(CurieProvider curieProvider, PinResetRequestController pinResetRequestController,
				UUID requestingUserId)
		{
			this(true, curieProvider, Optional.of(pinResetRequestController), Optional.of(requestingUserId));
		}

		private UserResourceAssembler(boolean includeAllLinks, CurieProvider curieProvider,
				Optional<PinResetRequestController> pinResetRequestController, Optional<UUID> requestingUserId)
		{
			super(UserController.class, UserResource.class);
			this.includeAllLinks = includeAllLinks;
			this.curieProvider = curieProvider;
			this.pinResetRequestController = pinResetRequestController;
			this.requestingUserId = requestingUserId;
		}

		@Override
		public UserResource toResource(UserDto user)
		{
			UserResource userResource = instantiateResource(user);
			addSelfLink(userResource, requestingUserId);
			if (isBuddyUser(user))
			{
				addUserPhotoLink(userResource);
			}
			else if (isOwnUser(user) && includeAllLinks)
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
					pinResetRequestController.get().addLinks(userResource);
					addSslRootCertificateLink(userResource);
					addAppleMobileConfigLink(userResource);
					addEditUserPhotoLink(userResource);
					addUserPhotoLink(userResource);
				}
				else
				{
					addConfirmMobileNumberLink(userResource);
					addResendMobileNumberConfirmationLink(userResource);
				}
			}
			return userResource;
		}

		private boolean isOwnUser(UserDto user)
		{
			return requestingUserId.map(ruid -> ruid.equals(user.getId())).orElse(false);
		}

		private boolean isBuddyUser(UserDto user)
		{
			return requestingUserId.map(ruid -> !ruid.equals(user.getId())).orElse(false);
		}

		private void addEditUserPhotoLink(UserResource userResource)
		{
			userResource.add(linkTo(methodOn(UserPhotoController.class).uploadUserPhoto(Optional.empty(), null,
					userResource.getContent().getId())).withRel("editUserPhoto").expand());
		}

		private void addUserPhotoLink(UserResource userResource)
		{
			userResource.getContent().getPrivateData().getUserPhotoId().ifPresent(userPhotoId -> userResource
					.add(linkTo(methodOn(UserPhotoController.class).getUserPhoto(userPhotoId)).withRel("userPhoto")));
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

		private static void addSelfLink(Resource<UserDto> userResource, Optional<UUID> requestingUserId)
		{
			if (userResource.getContent().getId() == null)
			{
				// removed user
				return;
			}

			userResource.add(UserController.getUserLink(Link.REL_SELF, userResource.getContent().getId(), requestingUserId));
		}

		private static void addEditLink(Resource<UserDto> userResource)
		{
			userResource.add(linkTo(methodOn(UserController.class).updateUser(Optional.empty(), null,
					userResource.getContent().getId(), null, null)).withRel(JsonRootRelProvider.EDIT_REL).expand());
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
