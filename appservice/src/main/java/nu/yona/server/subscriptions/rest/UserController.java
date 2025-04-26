/*******************************************************************************
 * Copyright (c) 2015, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import jakarta.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.servlet.http.HttpServletRequest;
import nu.yona.server.DOSProtectionService;
import nu.yona.server.analysis.rest.ActivityControllerBase;
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
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.ErrorResponseDto;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.rest.RestUtil;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.Require;
import nu.yona.server.util.TimeUtil;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/users", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserController extends ControllerBase
{
	public static final String REQUESTING_USER_ID_PARAM = "requestingUserId";
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

	@GetMapping(value = "/{userId}")
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = TEMP_PASSWORD_PARAM, required = false) String tempPasswordStr,
			@RequestParam(value = REQUESTING_USER_ID_PARAM, required = true) String requestingUserIdStr,
			@RequestParam(value = REQUESTING_DEVICE_ID_PARAM, required = false) String requestingDeviceIdStr,
			@PathVariable UUID userId)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		Optional<String> passwordToUse = getPasswordToUse(password, tempPassword);
		UUID requestingUserId = RestUtil.parseUuid(requestingUserIdStr);
		return getUser(requestingUserId, requestingDeviceIdStr, userId, tempPassword.isPresent(), passwordToUse);
	}

	private HttpEntity<UserResource> getUser(UUID requestingUserId, String requestingDeviceIdStr, UUID userId,
			boolean isCreatedOnBuddyRequest, Optional<String> password)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(requestingUserId)))
		{
			if (requestingUserId.equals(userId))
			{
				return getOwnUser(requestingUserId, requestingDeviceIdStr, isCreatedOnBuddyRequest);
			}
			return getBuddyUser(userId, requestingUserId);
		}
	}

	private HttpEntity<UserResource> getOwnUser(UUID userId, String requestingDeviceIdStr, boolean isCreatedOnBuddyRequest)
	{
		UserDto user = userService.getUser(userId, isCreatedOnBuddyRequest);
		Optional<UUID> requestingDeviceId = determineRequestingDeviceId(user, requestingDeviceIdStr, isCreatedOnBuddyRequest);
		if (requestingDeviceId.isPresent() && user.getOwnPrivateData().getDevices().map(d -> d.size() > 1).orElse(false))
		{
			// User has multiple devices
			deviceService.removeDuplicateDefaultDevices(user, requestingDeviceId.get());
			user = userService.getUser(userId, isCreatedOnBuddyRequest);
		}
		return createOkResponse(user, createResourceAssemblerForOwnUser(userId, requestingDeviceId));
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
		return requestingDeviceIdStr == null ?
				Optional.of(deviceService.getDefaultDeviceId(user)) :
				Optional.of(RestUtil.parseUuid(requestingDeviceIdStr));
	}

	@PostMapping(value = "/")
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public HttpEntity<UserResource> addUser(
			@RequestParam(value = "overwriteUserConfirmationCode", required = false) String overwriteUserConfirmationCode,
			@RequestBody PostPutUserDto postPutUser, HttpServletRequest request)
	{
		assertValidDeviceData(postPutUser);
		UserDto user = convertToUser(postPutUser);
		// use DOS protection to prevent overwrite user confirmation code brute forcing,
		// and to prevent enumeration of all occupied mobile numbers
		return dosProtectionService.executeAttempt(getAddUserLinkBuilder().toUri(), request,
				yonaProperties.getSecurity().getMaxCreateUserAttemptsPerTimeWindow(),
				() -> addUser(Optional.ofNullable(overwriteUserConfirmationCode), user));
	}

	private static void assertValidDeviceData(PostPutUserDto postPutUser)
	{
		if (postPutUser.deviceName == null)
		{
			String hint = "If the device name is not provided, the other properties should not be provided either";
			Require.isNull(postPutUser.deviceOperatingSystemStr,
					() -> InvalidDataException.extraProperty(PostPutUserDto.DEVICE_OPERATING_SYSTEM_PROPERTY, hint));
			Require.isNull(postPutUser.deviceAppVersion,
					() -> InvalidDataException.extraProperty(PostPutUserDto.DEVICE_APP_VERSION_PROPERTY, hint));
			Require.isNull(postPutUser.deviceAppVersionCode,
					() -> InvalidDataException.extraProperty(PostPutUserDto.DEVICE_APP_VERSION_CODE_PROPERTY, hint));
		}
		else
		{
			String hint = "If the device name is provided, the other properties must be present too";
			Require.isNonNull(postPutUser.deviceOperatingSystemStr,
					() -> InvalidDataException.missingProperty(PostPutUserDto.DEVICE_OPERATING_SYSTEM_PROPERTY, hint));
			Require.isNonNull(postPutUser.deviceAppVersion,
					() -> InvalidDataException.missingProperty(PostPutUserDto.DEVICE_APP_VERSION_PROPERTY, hint));
			Require.isNonNull(postPutUser.deviceAppVersionCode,
					() -> InvalidDataException.missingProperty(PostPutUserDto.DEVICE_APP_VERSION_CODE_PROPERTY, hint));
		}
	}

	private UserDto convertToUser(PostPutUserDto postPutUser)
	{
		Optional<DeviceRegistrationRequestDto> deviceRegistration = createDeviceRequestDto(postPutUser);
		return UserDto.createInstance(postPutUser.creationTime, postPutUser.firstName, postPutUser.lastName,
				postPutUser.mobileNumber, postPutUser.nickname,
				deviceRegistration.map(UserDeviceDto::createDeviceRegistrationInstance));
	}

	private Optional<DeviceRegistrationRequestDto> createDeviceRequestDto(PostPutUserDto postPutUser)
	{
		if (postPutUser.deviceName == null)
		{
			return Optional.empty();
		}
		String hint = "Mandatory when deviceName is provided";
		Require.isNonNull(postPutUser.deviceOperatingSystemStr,
				() -> InvalidDataException.missingProperty(PostPutUserDto.DEVICE_OPERATING_SYSTEM_PROPERTY, hint));
		Require.isNonNull(postPutUser.deviceAppVersion,
				() -> InvalidDataException.missingProperty(PostPutUserDto.DEVICE_APP_VERSION_PROPERTY, hint));
		Require.isNonNull(postPutUser.deviceAppVersionCode,
				() -> InvalidDataException.missingProperty(PostPutUserDto.DEVICE_APP_VERSION_CODE_PROPERTY, hint));
		return Optional.of(new DeviceRegistrationRequestDto(postPutUser.deviceName, postPutUser.deviceOperatingSystemStr,
				postPutUser.deviceAppVersion, postPutUser.deviceAppVersionCode,
				Optional.ofNullable(postPutUser.deviceFirebaseInstanceId)));
	}

	@PutMapping(value = "/{userId}")
	@ResponseBody
	public HttpEntity<UserResource> updateUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
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
			Require.isNonNull(requestingDeviceIdStr,
					() -> InvalidDataException.missingRequestParameter(REQUESTING_DEVICE_ID_PARAM,
							"This parameter is mandatory when '" + TEMP_PASSWORD_PARAM + "' is not provided"));
			return updateUser(password, userId, RestUtil.parseUuid(requestingDeviceIdStr), user, request);
		}
	}

	private HttpEntity<UserResource> updateUser(Optional<String> password, UUID userId, UUID requestingDeviceId, UserDto user,
			HttpServletRequest request)
	{
		Require.that(user.getOwnPrivateData().getDevices().orElse(Collections.emptySet()).isEmpty(),
				() -> InvalidDataException.extraProperty(PostPutUserDto.DEVICE_NAME_PROPERTY,
						"Embedding devices in an update request is only allowed when updating a user created on buddy request"));
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
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
		Require.isNotPresent(password, InvalidDataException::appProvidedPasswordNotSupported);
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

	@DeleteMapping(value = "/{userId}")
	@ResponseBody
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			userService.deleteUser(userId, Optional.ofNullable(messageStr));
		}
	}

	@PostMapping(value = "/{userId}/confirmMobileNumber")
	@ResponseBody
	public HttpEntity<UserResource> confirmMobileNumber(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestParam(value = REQUESTING_DEVICE_ID_PARAM, required = true) UUID requestingDeviceId,
			@RequestBody ConfirmationCodeDto mobileNumberConfirmation)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createOkResponse(userService.confirmMobileNumber(userId, mobileNumberConfirmation.getCode()),
					createResourceAssemblerForOwnUser(userId, Optional.of(requestingDeviceId)));
		}
	}

	@PostMapping(value = "/{userId}/resendMobileNumberConfirmationCode")
	@ResponseBody
	public ResponseEntity<Void> resendMobileNumberConfirmationCode(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			userService.resendMobileNumberConfirmationCode(userId);
			return createNoContentResponse();
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

	static WebMvcLinkBuilder getAddUserLinkBuilder()
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.addUser(null, null, null));
	}

	static WebMvcLinkBuilder getUpdateUserLinkBuilder(UUID userId, Optional<UUID> requestingDeviceId)
	{
		return linkTo(methodOn(UserController.class).updateUser(Optional.empty(), null, userId,
				requestingDeviceId.map(UUID::toString).orElse(null), null, null));
	}

	static WebMvcLinkBuilder getConfirmMobileNumberLinkBuilder(UUID userId, UUID requestingDeviceId)
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

	static Link getUserSelfLinkWithTempPassword(UUID userId, String tempPassword)
	{
		WebMvcLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).getUser(Optional.empty(), tempPassword, userId.toString(), null, userId));
		return linkBuilder.withSelfRel().expand(OMITTED_PARAMS);
	}

	private static Link getConfirmMobileLink(UUID userId, Optional<UUID> requestingDeviceId)
	{
		WebMvcLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).confirmMobileNumber(Optional.empty(), userId, requestingDeviceId.orElse(null),
						null));
		return linkBuilder.withRel("confirmMobileNumber");
	}

	public static Link getResendMobileNumberConfirmationLink(UUID userId)
	{
		WebMvcLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).resendMobileNumberConfirmationCode(Optional.empty(), userId));
		return linkBuilder.withRel("resendMobileNumberConfirmationCode");
	}

	private static Link getUserLink(LinkRelation rel, UUID userId, UUID requestingUserId, Optional<UUID> requestingDeviceId)
	{
		String requestingUserIdStr = requestingUserId.toString();
		String requestingDeviceIdStr = requestingDeviceId.map(UUID::toString).orElse(null);
		return linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, requestingUserIdStr, requestingDeviceIdStr,
				userId)).withRel(rel).expand(OMITTED_PARAMS);
	}

	public static Link getUserLink(LinkRelation rel, UUID userId, Optional<UUID> requestingDeviceId)
	{
		return getUserLink(rel, userId, userId, requestingDeviceId);
	}

	public static Link getBuddyUserLink(LinkRelation rel, UUID userId, UUID requestingUserId)
	{
		return getUserLink(rel, userId, requestingUserId, Optional.empty());
	}

	static class PostPutUserDto
	{
		static final String DEVICE_NAME_PROPERTY = "deviceName";
		static final String DEVICE_OPERATING_SYSTEM_PROPERTY = "deviceOperatingSystem";
		static final String DEVICE_APP_VERSION_PROPERTY = "deviceAppVersion";
		static final String DEVICE_APP_VERSION_CODE_PROPERTY = "deviceAppVersionCode";
		static final String DEVICE_FIREBASE_INSTANCE_ID_PROPERTY = "deviceFirebaseInstanceId";
		private final Optional<LocalDateTime> creationTime;
		private final String firstName;
		private final String lastName;
		private final String mobileNumber;
		private final String nickname;
		private final String deviceName;
		private final String deviceOperatingSystemStr;
		private final String deviceAppVersion;
		private final Integer deviceAppVersionCode;
		private final String deviceFirebaseInstanceId;

		@JsonCreator
		public PostPutUserDto(
				@JsonFormat(pattern = nu.yona.server.Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("creationTime") Optional<ZonedDateTime> creationTime,
				@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
				@JsonProperty("mobileNumber") String mobileNumber, @JsonProperty("nickname") String nickname,
				@JsonProperty(value = DEVICE_NAME_PROPERTY, required = false) String deviceName,
				@JsonProperty(value = DEVICE_OPERATING_SYSTEM_PROPERTY, required = false) String deviceOperatingSystem,
				@JsonProperty(value = DEVICE_APP_VERSION_PROPERTY, required = false) String deviceAppVersion,
				@JsonProperty(value = DEVICE_APP_VERSION_CODE_PROPERTY, required = false) Integer deviceAppVersionCode,
				@JsonProperty(value = DEVICE_FIREBASE_INSTANCE_ID_PROPERTY, required = false) String deviceFirebaseInstanceId,
				@JsonProperty("_links") Object ignored1, @JsonProperty("yonaPassword") Object ignored2)
		{
			this.creationTime = creationTime.map(TimeUtil::toUtcLocalDateTime);
			this.firstName = firstName;
			this.lastName = lastName;
			this.mobileNumber = mobileNumber;
			this.nickname = nickname;
			this.deviceName = deviceName;
			this.deviceOperatingSystemStr = deviceOperatingSystem;
			this.deviceAppVersion = deviceAppVersion;
			this.deviceAppVersionCode = deviceAppVersionCode;
			this.deviceFirebaseInstanceId = deviceFirebaseInstanceId;
		}
	}

	enum UserResourceRepresentation
	{
		MINIMAL(u -> false, u -> false, u -> false), BUDDY_USER(u -> true, u -> false, u -> false), OWN_USER(
			UserDto::isMobileNumberConfirmed, u -> !u.isMobileNumberConfirmed(), UserDto::isMobileNumberConfirmed);

		final Predicate<UserDto> includeGeneralContent;
		final Predicate<UserDto> includeOwnUserNumNotConfirmedContent;
		final Predicate<UserDto> includeOwnUserNumConfirmedContent;

		UserResourceRepresentation(Predicate<UserDto> includeGeneralContent,
				Predicate<UserDto> includeOwnUserNumNotConfirmedContent, Predicate<UserDto> includeOwnUserNumConfirmedContent)
		{
			this.includeGeneralContent = includeGeneralContent;
			this.includeOwnUserNumNotConfirmedContent = includeOwnUserNumNotConfirmedContent;
			this.includeOwnUserNumConfirmedContent = includeOwnUserNumConfirmedContent;
		}
	}

	public static class UserResource extends EntityModel<UserDto>
	{
		private final CurieProvider curieProvider;
		private final UserResourceRepresentation representation;
		private final UUID requestingUserId;
		private final Optional<UUID> requestingDeviceId;

		@SuppressWarnings("deprecation") // Constructor will become protected, see spring-projects/spring-hateoas#1297
		public UserResource(CurieProvider curieProvider, UserResourceRepresentation representation, UserDto user,
				UUID requestingUserId, Optional<UUID> requestingDeviceId)
		{
			super(user);
			this.curieProvider = curieProvider;
			this.representation = representation;
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
		}

		@Override
		@Nonnull
		public UserDto getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		@JsonProperty("_embedded")
		@JsonInclude(Include.NON_EMPTY)
		public Map<String, Object> getEmbeddedResources()
		{
			UUID userId = getContent().getId();
			HashMap<String, Object> result = new HashMap<>();
			if (representation.includeGeneralContent.test(getContent()))
			{
				Optional<Set<DeviceBaseDto>> devices = getContent().getPrivateData().getDevices();
				devices.ifPresent(d -> result.put(curieProvider.getNamespacedRelFor(UserDto.DEVICES_REL).value(),
						DeviceController.createAllDevicesCollectionResource(userId, d, requestingDeviceId)));

				Optional<Set<GoalDto>> goals = getContent().getPrivateData().getGoalsIncludingHistoryItems();
				goals.ifPresent(g -> result.put(curieProvider.getNamespacedRelFor(UserDto.GOALS_REL).value(),
						GoalController.createAllGoalsCollectionResource(requestingUserId, userId, g)));
			}
			if (representation.includeOwnUserNumConfirmedContent.test(getContent()))
			{
				Set<BuddyDto> buddies = getContent().getOwnPrivateData().getBuddies();
				result.put(curieProvider.getNamespacedRelFor(UserDto.BUDDIES_REL).value(),
						BuddyController.createAllBuddiesCollectionResource(curieProvider, userId, buddies));
			}

			return result;
		}

		static WebMvcLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserId)
		{
			BuddyController methodOn = methodOn(BuddyController.class);
			return linkTo(methodOn.getAllBuddies(null, requestingUserId));
		}
	}

	public static class UserResourceAssembler extends RepresentationModelAssemblerSupport<UserDto, UserResource>
	{
		private final CurieProvider curieProvider;
		private final UUID requestingUserId;
		private final Optional<UUID> requestingDeviceId;
		private final Optional<PinResetRequestController> pinResetRequestController;
		private final UserResourceRepresentation representation;

		private UserResourceAssembler(UserResourceRepresentation representation, CurieProvider curieProvider,
				UUID requestingUserId, Optional<UUID> requestingDeviceId,
				Optional<PinResetRequestController> pinResetRequestController)
		{
			super(UserController.class, UserResource.class);
			this.representation = representation;
			this.curieProvider = curieProvider;
			this.pinResetRequestController = pinResetRequestController;
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
		}

		public static UserResourceAssembler createMinimalInstance(CurieProvider curieProvider, UUID requestingUserId)
		{
			return new UserResourceAssembler(UserResourceRepresentation.MINIMAL, curieProvider, requestingUserId,
					Optional.empty(), Optional.empty());
		}

		public static UserResourceAssembler createInstanceForBuddy(CurieProvider curieProvider, UUID requestingUserId)
		{
			return new UserResourceAssembler(UserResourceRepresentation.BUDDY_USER, curieProvider, requestingUserId,
					Optional.empty(), Optional.empty());
		}

		public static UserResourceAssembler createInstanceForOwnUser(CurieProvider curieProvider, UUID requestingUserId,
				Optional<UUID> requestingDeviceId, PinResetRequestController pinResetRequestController)
		{
			return new UserResourceAssembler(UserResourceRepresentation.OWN_USER, curieProvider, requestingUserId,
					requestingDeviceId, Optional.of(pinResetRequestController));
		}

		@Override
		public UserResource toModel(UserDto user)
		{
			UserResource userResource = instantiateModel(user);
			if (user.getPrivateData().isFetchable())
			{
				addSelfLink(userResource);
			}
			if (representation.includeGeneralContent.test(user))
			{
				addGeneralLinks(userResource);
			}
			if (representation.includeOwnUserNumNotConfirmedContent.test(user))
			{
				addOwnUserNumNotConfirmedLinks(user, userResource);
			}
			if (representation.includeOwnUserNumConfirmedContent.test(user))
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
			addMessagesLink(userResource);
			addDayActivityOverviewsLink(userResource);
			addWeekActivityOverviewsLink(userResource);
			addDayActivityOverviewsWithBuddiesLink(userResource);
			addNewDeviceRequestLink(userResource);
			pinResetRequestController.get().addLinks(userResource);
			addEditUserPhotoLink(userResource);
		}

		private void addEditUserPhotoLink(UserResource userResource)
		{
			userResource.add(linkTo(methodOn(UserPhotoController.class).uploadUserPhoto(Optional.empty(), null,
					userResource.getContent().getId())).withRel("editUserPhoto").expand());
		}

		private void addUserPhotoLinkIfPhotoPresent(UserResource userResource)
		{
			userResource.getContent().getPrivateData().getUserPhotoId().ifPresent(userPhotoId -> userResource.add(
					linkTo(methodOn(UserPhotoController.class).getUserPhoto(userPhotoId)).withRel("userPhoto")));
		}

		@Override
		protected UserResource instantiateModel(UserDto user)
		{
			return new UserResource(curieProvider, representation, user, requestingUserId, requestingDeviceId);
		}

		private void addSelfLink(EntityModel<UserDto> userResource)
		{
			if (userResource.getContent().getId() == null)
			{
				// removed user
				return;
			}

			userResource.add(
					UserController.getUserLink(IanaLinkRelations.SELF, userResource.getContent().getId(), requestingUserId,
							requestingDeviceId));
		}

		private void addEditLink(EntityModel<UserDto> userResource)
		{
			userResource.add(getUpdateUserLinkBuilder(userResource.getContent().getId(), requestingDeviceId).withRel(
					IanaLinkRelations.EDIT).expand());
		}

		private void addConfirmMobileNumberLink(EntityModel<UserDto> userResource)
		{
			userResource.add(UserController.getConfirmMobileLink(userResource.getContent().getId(), requestingDeviceId));
		}

		private static void addResendMobileNumberConfirmationLink(EntityModel<UserDto> userResource)
		{
			userResource.add(UserController.getResendMobileNumberConfirmationLink(userResource.getContent().getId()));
		}

		private void addWeekActivityOverviewsLink(UserResource userResource)
		{
			userResource.add(UserActivityController.getUserWeekActivityOverviewsLinkBuilder(userResource.getContent().getId())
					.withRel(ActivityControllerBase.WEEK_OVERVIEW_REL));
		}

		private void addDayActivityOverviewsLink(UserResource userResource)
		{
			userResource.add(UserActivityController.getUserDayActivityOverviewsLinkBuilder(userResource.getContent().getId())
					.withRel(ActivityControllerBase.DAY_OVERVIEW_REL));
		}

		private void addDayActivityOverviewsWithBuddiesLink(UserResource userResource)
		{
			userResource.add(
					UserActivityController.getDayActivityOverviewsWithBuddiesLinkBuilder(userResource.getContent().getId(),
							requestingDeviceId.get()).withRel("dailyActivityReportsWithBuddies"));
		}

		private void addMessagesLink(UserResource userResource)
		{
			userResource.add(MessageController.getMessagesLink(userResource.getContent().getId()));
		}

		private void addNewDeviceRequestLink(UserResource userResource)
		{
			userResource.add(
					NewDeviceRequestController.getNewDeviceRequestLinkBuilder(userResource.getContent().getMobileNumber())
							.withRel("newDeviceRequest"));
		}
	}
}
