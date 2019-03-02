/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.ErrorResponseDto;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.rest.RestUtil;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.Require;

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
		return requestingDeviceIdStr == null ? Optional.of(deviceService.getDefaultDeviceId(user))
				: Optional.of(RestUtil.parseUuid(requestingDeviceIdStr));
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
					() -> InvalidDataException.extraProperty("deviceOperatingSystem", hint));
			Require.isNull(postPutUser.deviceAppVersion, () -> InvalidDataException.extraProperty("deviceAppVersion", hint));
			Require.isNull(postPutUser.deviceAppVersionCode,
					() -> InvalidDataException.extraProperty("deviceAppVersionCode", hint));
		}
		else
		{
			String hint = "If the device name is provided, the other properties must be present too";
			Require.isNonNull(postPutUser.deviceOperatingSystemStr,
					() -> InvalidDataException.missingProperty("deviceOperatingSystem", hint));
			Require.isNonNull(postPutUser.deviceAppVersion, () -> InvalidDataException.missingProperty("deviceAppVersion", hint));
			Require.isNonNull(postPutUser.deviceAppVersionCode,
					() -> InvalidDataException.missingProperty("deviceAppVersionCode", hint));
		}
	}

	private UserDto convertToUser(PostPutUserDto postPutUser)
	{
		Optional<DeviceRegistrationRequestDto> deviceRegistration = postPutUser.deviceName == null ? Optional.empty()
				: Optional.of(new DeviceRegistrationRequestDto(postPutUser.deviceName, postPutUser.deviceOperatingSystemStr,
						postPutUser.deviceAppVersion, postPutUser.deviceAppVersionCode, Optional.empty()));
		return UserDto.createInstance(postPutUser.firstName, postPutUser.lastName, postPutUser.mobileNumber, postPutUser.nickname,
				deviceRegistration.map(UserDeviceDto::createDeviceRegistrationInstance));
	}

	@PutMapping(value = "/{userId}")
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
			return updateUser(password, userId, RestUtil.parseUuid(requestingDeviceIdStr), user, request);
		}
	}

	private HttpEntity<UserResource> updateUser(Optional<String> password, UUID userId, UUID requestingDeviceId, UserDto user,
			HttpServletRequest request)
	{
		Require.that(user.getOwnPrivateData().getDevices().orElse(Collections.emptySet()).isEmpty(),
				() -> InvalidDataException.extraProperty("deviceName",
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
	public void deleteUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
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
	public HttpEntity<UserResource> confirmMobileNumber(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@RequestParam(value = REQUESTING_DEVICE_ID_PARAM, required = false) UUID requestingDeviceId,
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
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId)
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

	static Link getUserSelfLinkWithTempPassword(UUID userId, String tempPassword)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).getUser(Optional.empty(), tempPassword, userId.toString(), null, userId));
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

	private static Link getUserLink(String rel, UUID userId, UUID requestingUserId, Optional<UUID> requestingDeviceId)
	{
		String requestingUserIdStr = requestingUserId.toString();
		String requestingDeviceIdStr = requestingDeviceId.map(UUID::toString).orElse(null);
		return linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, requestingUserIdStr, requestingDeviceIdStr,
				userId)).withRel(rel).expand(OMITTED_PARAMS);
	}

	public static Link getUserLink(String rel, UUID userId, Optional<UUID> requestingDeviceId)
	{
		return getUserLink(rel, userId, userId, requestingDeviceId);
	}

	public static Link getBuddyUserLink(String rel, UUID userId, UUID requestingUserId)
	{
		return getUserLink(rel, userId, requestingUserId, Optional.empty());
	}

	static class PostPutUserDto
	{
		private final String firstName;
		private final String lastName;
		private final String mobileNumber;
		private final String nickname;
		private String deviceName;
		private String deviceOperatingSystemStr;
		private String deviceAppVersion;
		private Integer deviceAppVersionCode;

		@JsonCreator
		public PostPutUserDto(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
				@JsonProperty("mobileNumber") String mobileNumber, @JsonProperty("nickname") String nickname,
				@JsonProperty(value = "deviceName", required = false) String deviceName,
				@JsonProperty(value = "deviceOperatingSystem", required = false) String deviceOperatingSystem,
				@JsonProperty(value = "deviceAppVersion", required = false) String deviceAppVersion,
				@JsonProperty(value = "deviceAppVersionCode", required = false) Integer deviceAppVersionCode,
				@JsonProperty("_links") Object ignored1, @JsonProperty("yonaPassword") Object ignored2)
		{
			this.firstName = firstName;
			this.lastName = lastName;
			this.mobileNumber = mobileNumber;
			this.nickname = nickname;
			this.deviceName = deviceName;
			this.deviceOperatingSystemStr = deviceOperatingSystem;
			this.deviceAppVersion = deviceAppVersion;
			this.deviceAppVersionCode = deviceAppVersionCode;
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
		private UserResourceRepresentation representation;
		private UUID requestingUserId;
		private Optional<UUID> requestingDeviceId;

		public UserResource(CurieProvider curieProvider, UserResourceRepresentation representation, UserDto user,
				UUID requestingUserId, Optional<UUID> requestingDeviceId)
		{
			super(user);
			this.curieProvider = curieProvider;
			this.representation = representation;
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
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
						GoalController.createAllGoalsCollectionResource(requestingUserId, userId, g)));
			}
			if (representation.includeOwnUserNumConfirmedContent.apply(getContent()))
			{
				Set<BuddyDto> buddies = getContent().getOwnPrivateData().getBuddies();
				result.put(curieProvider.getNamespacedRelFor(UserDto.BUDDIES_REL_NAME),
						BuddyController.createAllBuddiesCollectionResource(curieProvider, userId, buddies));
			}

			return result;
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
			userResource.getContent().getPrivateData().getUserPhotoId().ifPresent(userPhotoId -> userResource
					.add(linkTo(methodOn(UserPhotoController.class).getUserPhoto(userPhotoId)).withRel("userPhoto")));
		}

		@Override
		protected UserResource instantiateResource(UserDto user)
		{
			return new UserResource(curieProvider, representation, user, requestingUserId, requestingDeviceId);
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
	}
}
