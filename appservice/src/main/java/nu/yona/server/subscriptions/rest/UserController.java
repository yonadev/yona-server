/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.DOSProtectionService;
import nu.yona.server.analysis.rest.UserActivityController;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.device.rest.DeviceController;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.device.service.DeviceRegistrationRequestDto;
import nu.yona.server.device.service.DeviceService;
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
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.VPNProfileDto;
import nu.yona.server.util.Require;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/users", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserController extends ControllerBase
{
	private static final String REQUESTING_USER_ID_PARAM = "requestingUserId";
	public static final String REQUESTING_DEVICE_ID_PARAM = "requestingDeviceId";
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
	private DeviceService deviceService;

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
	@Qualifier("sslRootCertificate")
	private X509Certificate sslRootCertificate; // YD-544

	@RequestMapping(value = "/{userId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = TEMP_PASSWORD_PARAM, required = false) String tempPasswordStr,
			@RequestParam(value = REQUESTING_USER_ID_PARAM, required = false) String requestingUserIdStr,
			@RequestParam(value = REQUESTING_DEVICE_ID_PARAM, required = false) String requestingDeviceIdStr,
			@RequestParam(value = INCLUDE_PRIVATE_DATA_PARAM, required = false, defaultValue = "false") String includePrivateDataStr,
			@PathVariable UUID userId)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		Optional<String> passwordToUse = getPasswordToUse(password, tempPassword);
		return Optional.ofNullable(determineRequestingUserId(userId, requestingUserIdStr, includePrivateDataStr))
				.map(UUID::fromString)
				.map(ruid -> getUser(ruid, requestingDeviceIdStr, userId, tempPassword.isPresent(), passwordToUse))
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

	private HttpEntity<UserResource> getUser(UUID requestingUserId, String requestingDeviceIdStr, UUID userId,
			boolean isCreatedOnBuddyRequest, Optional<String> password)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.canAccessPrivateData(requestingUserId)))
		{
			if (requestingUserId.equals(userId))
			{
				return getOwnUser(requestingUserId, requestingDeviceIdStr, userId, isCreatedOnBuddyRequest);
			}
			return getBuddyUser(userId, requestingUserId);
		}
	}

	private HttpEntity<UserResource> getOwnUser(UUID requestingUserId, String requestingDeviceIdStr, UUID userId,
			boolean isCreatedOnBuddyRequest)
	{
		UserDto user = userService.getPrivateUser(userId, isCreatedOnBuddyRequest);
		Optional<UUID> requestingDeviceId = determineRequestingDeviceId(user, requestingDeviceIdStr, isCreatedOnBuddyRequest);
		if (requestingDeviceId.isPresent() && user.getOwnPrivateData().getDevices().map(d -> d.size() > 1).orElse(false))
		{
			// User has multiple devices
			deviceService.removeDuplicateDefaultDevices(user, requestingDeviceId.get());
			user = userService.getPrivateUser(userId, isCreatedOnBuddyRequest);
		}
		return createOkResponse(user, createResourceAssemblerForOwnUser(requestingUserId, requestingDeviceId));
	}

	private ResponseEntity<UserResource> getBuddyUser(UUID userId, UUID requestingUserId)
	{
		return createOkResponse(buddyService.getUserOfBuddy(requestingUserId, userId),
				createResourceAssemblerForBuddy(requestingUserId));
	}

	private Optional<UUID> determineRequestingDeviceId(UserDto user, String requestingDeviceIdStr,
			boolean isCreatedOnBuddyRequest)
	{
		if (isCreatedOnBuddyRequest)
		{
			return Optional.empty();
		}
		return requestingDeviceIdStr == null ? Optional.of(deviceService.getDefaultDeviceId(user))
				: Optional.of(UUID.fromString(requestingDeviceIdStr));
	}

	private HttpEntity<UserResource> getPublicUser(UUID userId)
	{
		return createOkResponse(userService.getPublicUser(userId), createResourceAssemblerForPublicUser());
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
			@RequestParam(value = REQUESTING_DEVICE_ID_PARAM, required = false) String requestingDeviceIdStr,
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
			return updateUser(password, userId, UUID.fromString(requestingDeviceIdStr), user, request);
		}
	}

	private HttpEntity<UserResource> updateUser(Optional<String> password, UUID userId, UUID requestingDeviceId, UserDto user,
			HttpServletRequest request)
	{
		assert !user.getOwnPrivateData().getDevices().orElse(Collections.emptySet())
				.isEmpty() : "Embedding devices in an update request is only allowed when updating a user created on buddy request";
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			// use DOS protection to prevent enumeration of all occupied mobile numbers
			return dosProtectionService.executeAttempt(getUpdateUserLinkBuilder(userId, Optional.of(requestingDeviceId)).toUri(),
					request, yonaProperties.getSecurity().getMaxUpdateUserAttemptsPerTimeWindow(),
					() -> updateUser(userId, requestingDeviceId, user));
		}
	}

	private HttpEntity<UserResource> updateUserCreatedOnBuddyRequest(Optional<String> password, UUID userId, UserDto user,
			String tempPassword)
	{
		Require.isFalse(password.isPresent(), InvalidDataException::appProvidedPasswordNotSupported);
		addDefaultDeviceIfNotProvided(user);
		try (CryptoSession cryptoSession = CryptoSession.start(SecretKeyUtil.generateRandomSecretKey()))
		{
			UserDto updatedUser = userService.updateUserCreatedOnBuddyRequest(userId, tempPassword, user);
			UUID defaultDeviceId = deviceService.getDefaultDeviceId(updatedUser);
			return createOkResponse(updatedUser, createResourceAssemblerForOwnUser(userId, Optional.of(defaultDeviceId)));
		}
	}

	private void addDefaultDeviceIfNotProvided(UserDto user)
	{
		Set<DeviceBaseDto> devices = user.getOwnPrivateData().getDevices().orElse(Collections.emptySet());
		if (devices.isEmpty())
		{
			devices.add(deviceService.createDefaultUserDeviceDto());
		}
	}

	private HttpEntity<UserResource> updateUser(UUID userId, UUID requestingDeviceId, UserDto user)
	{
		return createOkResponse(userService.updateUser(userId, user),
				createResourceAssemblerForOwnUser(userId, Optional.of(requestingDeviceId)));
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
			@RequestParam(value = REQUESTING_DEVICE_ID_PARAM, required = false) UUID requestingDeviceId,
			@RequestBody ConfirmationCodeDto mobileNumberConfirmation)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return createOkResponse(userService.confirmMobileNumber(userId, mobileNumberConfirmation.getCode()),
					createResourceAssemblerForOwnUser(userId, Optional.of(requestingDeviceId)));
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

	static ControllerLinkBuilder getUpdateUserLinkBuilder(UUID userId, Optional<UUID> requestingDeviceId)
	{
		return linkTo(methodOn(UserController.class).updateUser(Optional.empty(), null, userId,
				requestingDeviceId.map(UUID::toString).orElse(null), null, null));
	}

	static ControllerLinkBuilder getConfirmMobileNumberLinkBuilder(UUID userId, UUID requestingDeviceId)
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.confirmMobileNumber(Optional.empty(), userId, requestingDeviceId, null));
	}

	private HttpEntity<UserResource> addUser(Optional<String> overwriteUserConfirmationCode, UserDto user)
	{
		addDefaultDeviceIfNotProvided(user);
		try (CryptoSession cryptoSession = CryptoSession.start(SecretKeyUtil.generateRandomSecretKey()))
		{
			UserDto createdUser = userService.addUser(user, overwriteUserConfirmationCode);
			UUID requestingDeviceId = deviceService.getDefaultDeviceId(createdUser);
			return createResponse(createdUser, HttpStatus.CREATED,
					createResourceAssemblerForOwnUser(createdUser.getId(), Optional.of(requestingDeviceId)));
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

	public UserResourceAssembler createResourceAssemblerForOwnUser(UUID requestingUserId, Optional<UUID> requestingDeviceId)
	{
		return UserResourceAssembler.createInstanceForOwnUser(curieProvider, requestingUserId, requestingDeviceId,
				pinResetRequestController);
	}

	private UserResourceAssembler createResourceAssemblerForBuddy(UUID requestingUserId)
	{
		return UserResourceAssembler.createInstanceForBuddy(curieProvider, requestingUserId);
	}

	private UserResourceAssembler createResourceAssemblerForPublicUser()
	{
		return UserResourceAssembler.createPublicUserInstance(curieProvider);
	}

	static Link getUserSelfLinkWithTempPassword(UUID userId, String tempPassword)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).getUser(Optional.empty(), tempPassword, userId.toString(), null, null, userId));
		// Should call expand, but that's not done because of https://github.com/spring-projects/spring-hateoas/issues/703
		return linkBuilder.withSelfRel();
	}

	private static Link getConfirmMobileLink(UUID userId, Optional<UUID> requestingDeviceId)
	{
		ControllerLinkBuilder linkBuilder = linkTo(methodOn(UserController.class).confirmMobileNumber(Optional.empty(), userId,
				requestingDeviceId.orElse(null), null));
		return linkBuilder.withRel("confirmMobileNumber");
	}

	public static Link getResendMobileNumberConfirmationLink(UUID userId)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).resendMobileNumberConfirmationCode(Optional.empty(), userId));
		return linkBuilder.withRel("resendMobileNumberConfirmationCode");
	}

	private static Link getUserLink(String rel, UUID userId, Optional<UUID> requestingUserId, Optional<UUID> requestingDeviceId)
	{
		String requestingUserIdStr = requestingUserId.map(UUID::toString).orElse(null);
		String requestingDeviceIdStr = requestingDeviceId.map(UUID::toString).orElse(null);
		return linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, requestingUserIdStr, requestingDeviceIdStr,
				null, userId)).withRel(rel).expand(OMITTED_PARAMS);
	}

	public static Link getPublicUserLink(String rel, UUID userId)
	{
		return getUserLink(rel, userId, Optional.empty(), Optional.empty());
	}

	public static Link getPrivateUserLink(String rel, UUID userId, Optional<UUID> requestingDeviceId)
	{
		return getUserLink(rel, userId, Optional.of(userId), requestingDeviceId);
	}

	public static Link getBuddyUserLink(String rel, UUID userId, UUID requestingUserId)
	{
		return getUserLink(rel, userId, Optional.of(requestingUserId), Optional.empty());
	}

	@PostConstruct
	private void setSslRootCertificateCn() // YD-544
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
		private final Optional<DeviceRegistrationRequestDto> deviceRegistration;

		@JsonCreator
		public PostPutUserDto(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
				@JsonProperty("mobileNumber") String mobileNumber, @JsonProperty("nickname") String nickname,
				@JsonProperty(value = "deviceName", required = false) String deviceName,
				@JsonProperty(value = "deviceOperatingSystem", required = false) String deviceOperatingSystem,
				@JsonProperty(value = "deviceAppVersion", required = false) String deviceAppVersion,
				@JsonProperty("_links") Object ignored1, @JsonProperty("yonaPassword") Object ignored2)
		{
			this.firstName = firstName;
			this.lastName = lastName;
			this.mobileNumber = mobileNumber;
			this.nickname = nickname;
			this.deviceRegistration = deviceName == null ? Optional.empty()
					: Optional.of(new DeviceRegistrationRequestDto(deviceName, deviceOperatingSystem, deviceAppVersion));
		}

		Optional<UserDeviceDto> getDevice()
		{
			return deviceRegistration.map(UserDeviceDto::createDeviceRegistrationInstance);
		}
	}

	enum UserResourceRepresentation
	{
		MINIMAL(u -> false, u -> false, u -> false), BUDDY_USER(u -> true, u -> false, u -> false), OWN_USER(
				u -> u.isMobileNumberConfirmed(), u -> !u.isMobileNumberConfirmed(), u -> u.isMobileNumberConfirmed());

		final Function<UserDto, Boolean> includeGeneralContent;
		final Function<UserDto, Boolean> includeOwnUserNumNotConfirmedContent;
		final Function<UserDto, Boolean> includeOwnUserNumConfirmedContent;

		UserResourceRepresentation(Function<UserDto, Boolean> includeGeneralContent,
				Function<UserDto, Boolean> includeOwnUserNumNotConfirmedContent,
				Function<UserDto, Boolean> includeOwnUserNumConfirmedContent)
		{
			this.includeGeneralContent = includeGeneralContent;
			this.includeOwnUserNumNotConfirmedContent = includeOwnUserNumNotConfirmedContent;
			this.includeOwnUserNumConfirmedContent = includeOwnUserNumConfirmedContent;
		}
	}

	public static class UserResource extends Resource<UserDto>
	{
		private final CurieProvider curieProvider;
		private static String sslRootCertificateCn; // YD-544
		private UserResourceRepresentation representation;
		private Optional<UUID> requestingDeviceId;

		public UserResource(CurieProvider curieProvider, UserResourceRepresentation representation, UserDto user,
				Optional<UUID> requestingDeviceId)
		{
			super(user);
			this.curieProvider = curieProvider;
			this.representation = representation;
			this.requestingDeviceId = requestingDeviceId;
		}

		public static void setSslRootCertificateCn(String sslRootCertificateCn)
		{
			UserResource.sslRootCertificateCn = sslRootCertificateCn;
		}

		@JsonProperty("sslRootCertCN")
		@JsonInclude(Include.NON_EMPTY)
		public Optional<String> getSslRootCertCn() // YD-544
		{
			if (representation.includeOwnUserNumConfirmedContent.apply(getContent()))
			{
				return Optional.of(sslRootCertificateCn);
			}
			return Optional.empty();

		}

		@JsonProperty("_embedded")
		@JsonInclude(Include.NON_EMPTY)
		public Map<String, Object> getEmbeddedResources()
		{
			UUID userId = getContent().getId();
			HashMap<String, Object> result = new HashMap<>();
			if (representation.includeGeneralContent.apply(getContent()))
			{
				Optional<Set<DeviceBaseDto>> devices = getContent().getPrivateData().getDevices();
				devices.ifPresent(d -> result.put(curieProvider.getNamespacedRelFor(UserDto.DEVICES_REL_NAME),
						DeviceController.createAllDevicesCollectionResource(userId, d, requestingDeviceId)));

				Optional<Set<GoalDto>> goals = getContent().getPrivateData().getGoals();
				goals.ifPresent(g -> result.put(curieProvider.getNamespacedRelFor(UserDto.GOALS_REL_NAME),
						GoalController.createAllGoalsCollectionResource(userId, g)));
			}
			if (representation.includeOwnUserNumConfirmedContent.apply(getContent()))
			{
				Set<BuddyDto> buddies = getContent().getOwnPrivateData().getBuddies();
				result.put(curieProvider.getNamespacedRelFor(UserDto.BUDDIES_REL_NAME),
						BuddyController.createAllBuddiesCollectionResource(curieProvider, userId, buddies));
			}

			return result;
		}

		// Remove this method as part of YD-541
		@JsonInclude(Include.NON_EMPTY)
		public Resource<VPNProfileDto> getVpnProfile()
		{
			if (representation.includeOwnUserNumConfirmedContent.apply(getContent()))
			{
				return getContent().getOwnPrivateData().getVpnProfile()
						.map(DeviceController.DeviceResource::createVpnProfileResource).orElse(null);
			}
			return null;
		}

		static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserId)
		{
			BuddyController methodOn = methodOn(BuddyController.class);
			return linkTo(methodOn.getAllBuddies(null, requestingUserId));
		}
	}

	public static class UserResourceAssembler extends ResourceAssemblerSupport<UserDto, UserResource>
	{
		private final CurieProvider curieProvider;
		private final Optional<UUID> requestingUserId;
		private final Optional<UUID> requestingDeviceId;
		private final Optional<PinResetRequestController> pinResetRequestController;
		private final UserResourceRepresentation representation;

		private UserResourceAssembler(UserResourceRepresentation representation, CurieProvider curieProvider,
				Optional<UUID> requestingUserId, Optional<UUID> requestingDeviceId,
				Optional<PinResetRequestController> pinResetRequestController)
		{
			super(UserController.class, UserResource.class);
			this.representation = representation;
			this.curieProvider = curieProvider;
			this.pinResetRequestController = pinResetRequestController;
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
		}

		public static UserResourceAssembler createPublicUserInstance(CurieProvider curieProvider)
		{
			return new UserResourceAssembler(UserResourceRepresentation.MINIMAL, curieProvider, Optional.empty(),
					Optional.empty(), Optional.empty());
		}

		public static UserResourceAssembler createMinimalInstance(CurieProvider curieProvider, UUID requestingUserId)
		{
			return new UserResourceAssembler(UserResourceRepresentation.MINIMAL, curieProvider, Optional.of(requestingUserId),
					Optional.empty(), Optional.empty());
		}

		public static UserResourceAssembler createInstanceForBuddy(CurieProvider curieProvider, UUID requestingUserId)
		{
			return new UserResourceAssembler(UserResourceRepresentation.BUDDY_USER, curieProvider, Optional.of(requestingUserId),
					Optional.empty(), Optional.empty());
		}

		public static UserResourceAssembler createInstanceForOwnUser(CurieProvider curieProvider, UUID requestingUserId,
				Optional<UUID> requestingDeviceId, PinResetRequestController pinResetRequestController)
		{
			return new UserResourceAssembler(UserResourceRepresentation.OWN_USER, curieProvider, Optional.of(requestingUserId),
					requestingDeviceId, Optional.of(pinResetRequestController));
		}

		@Override
		public UserResource toResource(UserDto user)
		{
			UserResource userResource = instantiateResource(user);
			addSelfLink(userResource);
			if (representation.includeGeneralContent.apply(user))
			{
				addGeneralLinks(userResource);
			}
			if (representation.includeOwnUserNumNotConfirmedContent.apply(user))
			{
				addOwnUserNumNotConfirmedLinks(user, userResource);
			}
			if (representation.includeOwnUserNumConfirmedContent.apply(user))
			{
				addOwnUserNumConfirmedLinks(userResource);
			}
			return userResource;
		}

		private void addGeneralLinks(UserResource userResource)
		{
			addUserPhotoLinkIfPhotoPresent(userResource);
		}

		private void addOwnUserNumNotConfirmedLinks(UserDto user, UserResource userResource)
		{
			addEditLink(userResource);
			if (!user.isCreatedOnBuddyRequest())
			{
				addConfirmMobileNumberLink(userResource);
				addResendMobileNumberConfirmationLink(userResource);
			}
		}

		private void addOwnUserNumConfirmedLinks(UserResource userResource)
		{
			addEditLink(userResource);
			addPostOpenAppEventLink(userResource); // YD-544
			addMessagesLink(userResource);
			addDayActivityOverviewsLink(userResource);
			addWeekActivityOverviewsLink(userResource);
			addDayActivityOverviewsWithBuddiesLink(userResource);
			addNewDeviceRequestLink(userResource);
			addAppActivityLink(userResource); // YD-544
			pinResetRequestController.get().addLinks(userResource);
			addSslRootCertificateLink(userResource); // YD-544
			addAppleMobileConfigLink(userResource); // YD-544
			addEditUserPhotoLink(userResource);
		}

		private void addEditUserPhotoLink(UserResource userResource)
		{
			userResource.add(linkTo(methodOn(UserPhotoController.class).uploadUserPhoto(Optional.empty(), null,
					userResource.getContent().getId())).withRel("editUserPhoto").expand());
		}

		private void addUserPhotoLinkIfPhotoPresent(UserResource userResource)
		{
			userResource.getContent().getPrivateData().getUserPhotoId().ifPresent(userPhotoId -> userResource
					.add(linkTo(methodOn(UserPhotoController.class).getUserPhoto(userPhotoId)).withRel("userPhoto")));
		}

		private void addAppleMobileConfigLink(UserResource userResource)
		{
			userResource.add(linkTo(methodOn(DeviceController.class).getAppleMobileConfig(Optional.empty(),
					userResource.getContent().getId(), requestingDeviceId.get())).withRel("appleMobileConfig"));
		}

		private void addSslRootCertificateLink(Resource<UserDto> userResource)
		{
			userResource.add(linkTo(methodOn(StandardResourcesController.class).getSslRootCert()).withRel("sslRootCert"));
		}

		@Override
		protected UserResource instantiateResource(UserDto user)
		{
			return new UserResource(curieProvider, representation, user, requestingDeviceId);
		}

		private void addSelfLink(Resource<UserDto> userResource)
		{
			if (userResource.getContent().getId() == null)
			{
				// removed user
				return;
			}

			userResource.add(UserController.getUserLink(Link.REL_SELF, userResource.getContent().getId(), requestingUserId,
					requestingDeviceId));
		}

		private void addEditLink(Resource<UserDto> userResource)
		{
			userResource.add(getUpdateUserLinkBuilder(userResource.getContent().getId(), requestingDeviceId)
					.withRel(JsonRootRelProvider.EDIT_REL).expand());
		}

		private void addConfirmMobileNumberLink(Resource<UserDto> userResource)
		{
			userResource.add(UserController.getConfirmMobileLink(userResource.getContent().getId(), requestingDeviceId));
		}

		private static void addResendMobileNumberConfirmationLink(Resource<UserDto> userResource)
		{
			userResource.add(UserController.getResendMobileNumberConfirmationLink(userResource.getContent().getId()));
		}

		private void addPostOpenAppEventLink(Resource<UserDto> userResource)
		{
			userResource
					.add(DeviceController.getPostOpenAppEventLink(userResource.getContent().getId(), requestingDeviceId.get()));
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
			userResource.add(UserActivityController
					.getDayActivityOverviewsWithBuddiesLinkBuilder(userResource.getContent().getId(), requestingDeviceId.get())
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
			// IDs are available when the app activity link is relevant, so directly call get on Optional
			userResource.add(DeviceController.getAppActivityLink(requestingUserId.get(), requestingDeviceId.get()));
		}
	}
}
