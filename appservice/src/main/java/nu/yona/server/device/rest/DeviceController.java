/*******************************************************************************
 * Copyright (c) 2017, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.rest;

import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.Nonnull;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.PostConstruct;
import nu.yona.server.analysis.service.AnalysisEngineProxyService;
import nu.yona.server.analysis.service.AppActivitiesDto;
import nu.yona.server.analysis.service.AppActivitiesDto.Activity;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.rest.DeviceController.DeviceResource;
import nu.yona.server.device.service.AppOpenEventDto;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.device.service.DeviceRegistrationRequestDto;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.device.service.DeviceServiceException;
import nu.yona.server.device.service.DeviceUpdateRequestDto;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.RestConstants;
import nu.yona.server.rest.RestUtil;
import nu.yona.server.rest.StandardResourcesController;
import nu.yona.server.subscriptions.rest.AppleMobileConfigSigner;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.NewDeviceRequestDto;
import nu.yona.server.subscriptions.service.NewDeviceRequestService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.VPNProfileDto;
import nu.yona.server.util.Require;
import nu.yona.server.util.ThymeleafUtil;

@Controller
@ExposesResourceFor(DeviceResource.class)
@RequestMapping(value = "/users/{userId}/devices", produces = { MediaType.APPLICATION_JSON_VALUE })
public class DeviceController extends ControllerBase
{
	private static final String APP_VERSION_CODE_PROPERTY = "appVersionCode";

	private static final String APP_VERSION_PROPERTY = "appVersion";

	private static final String OPERATING_SYSTEM_PROPERTY = "operatingSystem";

	private static final String NAME_PROPERTY = "name";

	private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

	@Autowired
	private DeviceService deviceService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserController userController;

	@Autowired
	private AppleMobileConfigSigner appleMobileConfigSigner;

	@Autowired
	@Qualifier("appleMobileConfigTemplateEngine")
	private TemplateEngine templateEngine;

	@Autowired
	private NewDeviceRequestService newDeviceRequestService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private AnalysisEngineProxyService analysisEngineProxyService;

	@Autowired
	@Qualifier("sslRootCertificate")
	private X509Certificate sslRootCertificate;

	@GetMapping(value = "/")
	@ResponseBody
	public HttpEntity<CollectionModel<DeviceResource>> getAllDevices(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@RequestParam(value = UserController.REQUESTING_DEVICE_ID_PARAM, required = false) String requestingDeviceIdStr)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			Optional<UUID> requestingDeviceId = nullableStringToOptionalUuid(requestingDeviceIdStr);
			return createOkResponse(deviceService.getDevicesOfUser(userId), createResourceAssembler(userId, requestingDeviceId),
					getAllDevicesLinkBuilder(userId, requestingDeviceId));
		}
	}

	@GetMapping(value = "/{deviceId}")
	@ResponseBody
	public HttpEntity<DeviceResource> getDevice(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId,
			@RequestParam(value = UserController.REQUESTING_DEVICE_ID_PARAM, required = false) String requestingDeviceIdStr)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createOkResponse(deviceService.getDevice(userId, deviceId),
					createResourceAssembler(userId, nullableStringToOptionalUuid(requestingDeviceIdStr)));
		}
	}

	@PostMapping(value = "/")
	@ResponseBody
	public HttpEntity<UserResource> registerDevice(
			@RequestHeader(value = RestConstants.NEW_DEVICE_REQUEST_PASSWORD_HEADER) String newDeviceRequestPassword,
			@PathVariable UUID userId, @RequestBody DeviceRegistrationRequestDto request)
	{
		assertValidDeviceDataForRegister(request);
		NewDeviceRequestDto newDeviceRequest = newDeviceRequestService.getNewDeviceRequestForUser(userId,
				Optional.ofNullable(newDeviceRequestPassword));
		try (CryptoSession ignored = CryptoSession.start(newDeviceRequest.getYonaPassword(),
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			UserDeviceDto newDevice = deviceService.addDeviceToUser(userId,
					UserDeviceDto.createDeviceRegistrationInstance(request));
			return createResponse(userService.getUser(userId, false), HttpStatus.CREATED,
					userController.createResourceAssemblerForOwnUser(userId, Optional.of(newDevice.getId())));
		}
	}

	private static void assertValidDeviceDataForRegister(DeviceRegistrationRequestDto requestDto)
	{
		String hint = "All properties are mandatory, except for the FireBase ID";
		Require.isNonNull(requestDto.name, () -> InvalidDataException.missingProperty(NAME_PROPERTY, hint));
		Require.isNonNull(requestDto.operatingSystemStr,
				() -> InvalidDataException.missingProperty(OPERATING_SYSTEM_PROPERTY, hint));
		Require.isNonNull(requestDto.appVersion, () -> InvalidDataException.missingProperty(APP_VERSION_PROPERTY, hint));
		Require.that(requestDto.appVersionCode != 0, () -> InvalidDataException.missingProperty(APP_VERSION_CODE_PROPERTY, hint));
	}

	@PostMapping(value = "/{deviceId}/openApp")
	@ResponseBody
	public ResponseEntity<Void> postOpenAppEvent(@RequestHeader(value = RestConstants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId, @RequestBody @Nonnull AppOpenEventDto request)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			assertValidOpenAppEvent(request);
			deviceService.postOpenAppEvent(userId, deviceId, request.getOperatingSystem(),
					Optional.ofNullable(request.appVersion), request.appVersionCode);
			return createNoContentResponse();
		}
	}

	private void assertValidOpenAppEvent(AppOpenEventDto request)
	{
		if (request.operatingSystemStr == null)
		{
			Require.isNull(request.appVersion, () -> InvalidDataException.extraProperty(APP_VERSION_PROPERTY,
					"If the operating system is not provided, the other properties should not be provided either"));
			Require.that(request.appVersionCode == 0, () -> InvalidDataException.extraProperty(APP_VERSION_CODE_PROPERTY,
					"If the operating system is not provided, the other properties should not be provided either"));
		}
		else
		{
			Require.isNonNull(request.appVersion, () -> InvalidDataException.missingProperty(APP_VERSION_PROPERTY,
					"If the operating system is provided, the other properties must be present too"));
			Require.that(request.appVersionCode != 0, () -> InvalidDataException.missingProperty(APP_VERSION_CODE_PROPERTY,
					"If the operating system is provided, the other properties must be present too"));
		}
	}

	@GetMapping(value = "/{deviceId}/apple.mobileconfig")
	@ResponseBody
	public ResponseEntity<byte[]> getAppleMobileConfig(
			@RequestHeader(value = RestConstants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID deviceId)
	{
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "x-apple-aspen-config"));
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(getDeviceSpecificAppleMobileConfig(deviceService.getDevice(userId, deviceId)), headers,
					HttpStatus.OK);
		}
	}

	private byte[] getDeviceSpecificAppleMobileConfig(UserDeviceDto device)
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("ldapUsername", device.getVpnProfile().getVpnLoginId());
		ctx.setVariable("ldapPassword", device.getVpnProfile().getVpnPassword());

		return signIfEnabled(templateEngine.process("apple.mobileconfig", ctx).getBytes(StandardCharsets.UTF_8));
	}

	private byte[] signIfEnabled(byte[] unsignedMobileConfig)
	{
		if (yonaProperties.getAppleMobileConfig().isSigningEnabled())
		{
			return appleMobileConfigSigner.sign(unsignedMobileConfig);
		}
		return unsignedMobileConfig;
	}

	@PutMapping(value = "/{deviceId}")
	@ResponseBody
	public HttpEntity<DeviceResource> updateDevice(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId,
			@RequestParam(value = UserController.REQUESTING_DEVICE_ID_PARAM, required = false) String requestingDeviceIdStr,
			@RequestBody DeviceUpdateRequestDto request)
	{
		assertValidDeviceDataForUpdate(request);
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createOkResponse(deviceService.updateDevice(userId, deviceId, request),
					createResourceAssembler(userId, nullableStringToOptionalUuid(requestingDeviceIdStr)));
		}
	}

	private static void assertValidDeviceDataForUpdate(DeviceUpdateRequestDto requestDto)
	{
		String hint = "The name is mandatory and the FireBase ID optional";
		Require.isNonNull(requestDto.name, () -> InvalidDataException.missingProperty(NAME_PROPERTY, hint));
	}

	@DeleteMapping(value = "/{deviceId}")
	@ResponseBody
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteDevice(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID deviceId,
			@RequestParam(value = UserController.REQUESTING_DEVICE_ID_PARAM, required = false) String requestingDeviceIdStr)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			deviceService.deleteDevice(userId, deviceId);
		}
	}

	/*
	 * Adds app activity registered by the Yona app. This request is delegated to the analysis engine service.
	 * @param password User password, validated before adding the activity.
	 * @param appActivities Because it may be that multiple app activities may have taken place during the time the network is
	 * down, accept an array of activities.
	 */
	@PostMapping(value = "/{deviceId}/appActivity/")
	@ResponseBody
	public ResponseEntity<Void> addAppActivity(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId, @RequestBody AppActivitiesDto appActivities)
	{
		if (appActivities.getActivities().length > yonaProperties.getAnalysisService().getAppActivityCountIgnoreThreshold())
		{
			logTooLongAppActivityBatch(userId, appActivities);
			return createOkResponse();
		}
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			UserDto userDto = userService.getUser(userId);
			autoregisterAndroid(userDto, deviceId);
			UUID userAnonymizedId = userDto.getOwnPrivateData().getUserAnonymizedId();
			UUID deviceAnonymizedId = deviceService.getDeviceAnonymizedId(userDto, deviceId);
			analysisEngineProxyService.analyzeAppActivity(userAnonymizedId, deviceAnonymizedId, appActivities);
			return createNoContentResponse();
		}
	}

	/**
	 * Autoregisters the given device as running Android. As of today, only the Android app is capable of sending app activities,
	 * so if the operating system of the requesting device is marked as UNKNOWN, we can improve the registration and mark it as
	 * ANDROID.
	 *
	 * @param userDto  The user sending the app activities
	 * @param deviceId The ID of the device for which the app activities are being sent
	 */
	private void autoregisterAndroid(UserDto userDto, UUID deviceId)
	{
		UserDeviceDto device = userDto.getOwnPrivateData().getOwnDevices().stream().filter(d -> d.getId().equals(deviceId))
				.findAny().orElseThrow(() -> DeviceServiceException.notFoundById(deviceId));
		if (device.getOperatingSystem() == OperatingSystem.UNKNOWN)
		{
			// The device is registered as UNKNOWN, but given that it registers app activities, it's apparently ANDROID, so update
			// the operating system
			deviceService.updateOperatingSystem(userDto.getId(), deviceId, OperatingSystem.ANDROID);
		}
	}

	private void logTooLongAppActivityBatch(UUID userId, AppActivitiesDto appActivities)
	{
		int numAppActivities = appActivities.getActivities().length;
		List<Activity> appActivityCollection = Arrays.asList(appActivities.getActivities());
		Comparator<? super Activity> comparator = Comparator.comparing(Activity::getStartTime);
		ZonedDateTime minStartTime = Collections.min(appActivityCollection, comparator).getStartTime();
		ZonedDateTime maxStartTime = Collections.max(appActivityCollection, comparator).getStartTime();
		logger.error(
				"User with ID {} posts too many ({}) app activities, with start dates ranging from {} to {} (device time: {}). App activities ignored.",
				userId, numAppActivities, minStartTime, maxStartTime, appActivities.getDeviceDateTime());
	}

	@PostMapping(value = "/{deviceId}/vpnStatus/")
	@ResponseBody
	public ResponseEntity<Void> postVpnStatusChangeEvent(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId, @RequestBody VpnStatusDto vpnStatus)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			deviceService.registerVpnStatusChangeEvent(userId, deviceId, vpnStatus.vpnConnected);
			return createNoContentResponse();
		}
	}

	public static Link getPostOpenAppEventLink(UUID userId, UUID deviceId)
	{
		WebMvcLinkBuilder linkBuilder = linkTo(
				methodOn(DeviceController.class).postOpenAppEvent(Optional.empty(), userId, deviceId, AppOpenEventDto.DUMMY));
		return linkBuilder.withRel("postOpenAppEvent");
	}

	public static Link getAppActivityLink(UUID userId, UUID deviceId)
	{
		try
		{
			WebMvcLinkBuilder linkBuilder = linkTo(
					methodOn(DeviceController.class).addAppActivity(Optional.empty(), userId, deviceId, AppActivitiesDto.DUMMY));
			return linkBuilder.withRel("appActivity");
		}
		catch (SecurityException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static Link getPostVpnStatusEventLink(UUID userId, UUID deviceId)
	{
		WebMvcLinkBuilder linkBuilder = linkTo(
				methodOn(DeviceController.class).postVpnStatusChangeEvent(Optional.empty(), userId, deviceId,
						VpnStatusDto.DUMMY));
		return linkBuilder.withRel("postVpnStatusEvent");
	}

	private DeviceResourceAssembler createResourceAssembler(UUID userId, Optional<UUID> requestingDeviceId)
	{
		return new DeviceResourceAssembler(userId, requestingDeviceId);
	}

	public static WebMvcLinkBuilder getAllDevicesLinkBuilder(UUID userId, Optional<UUID> requestingDeviceId)
	{
		DeviceController methodOn = methodOn(DeviceController.class);
		return linkTo(methodOn.getAllDevices(Optional.empty(), userId, optionalUuidToNullableString(requestingDeviceId)));
	}

	private static String optionalUuidToNullableString(Optional<UUID> optionalUuid)
	{
		return optionalUuid.map(UUID::toString).orElse(null);
	}

	private static Optional<UUID> nullableStringToOptionalUuid(String uuidStr)
	{
		return Optional.ofNullable(uuidStr).map(RestUtil::parseUuid);
	}

	public static WebMvcLinkBuilder getDeviceLinkBuilder(UUID userId, UUID deviceId, Optional<UUID> requestingDeviceId)
	{
		DeviceController methodOn = methodOn(DeviceController.class);
		return linkTo(methodOn.getDevice(Optional.empty(), userId, deviceId, optionalUuidToNullableString(requestingDeviceId)));
	}

	public static WebMvcLinkBuilder getRegisterDeviceLinkBuilder(UUID userId)
	{
		DeviceController methodOn = methodOn(DeviceController.class);
		return linkTo(methodOn.registerDevice(null, userId, DeviceRegistrationRequestDto.DUMMY));
	}

	public static CollectionModel<DeviceResource> createAllDevicesCollectionResource(UUID userId, Set<DeviceBaseDto> devices,
			Optional<UUID> requestingDeviceId)
	{
		return CollectionModel.of(new DeviceResourceAssembler(userId, requestingDeviceId).toCollectionModel(devices),
				DeviceController.getAllDevicesLinkBuilder(userId, requestingDeviceId).withSelfRel());
	}

	@PostConstruct
	private void setSslRootCertificateCn()
	{
		try
		{
			LdapName name = new LdapName(sslRootCertificate.getIssuerX500Principal().getName());
			DeviceResource.setSslRootCertificateCn(name.getRdn(0).getValue().toString());
		}
		catch (InvalidNameException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	static class VpnStatusDto
	{
		public static final VpnStatusDto DUMMY = new VpnStatusDto();
		private final boolean vpnConnected;

		@JsonCreator
		public VpnStatusDto(@JsonProperty("vpnConnected") boolean vpnConnected)
		{
			this.vpnConnected = vpnConnected;
		}

		// Only to create a dummy instance
		private VpnStatusDto()
		{
			vpnConnected = false;
		}
	}

	public static class DeviceResource extends EntityModel<DeviceBaseDto>
	{
		private static String sslRootCertificateCn;
		private final boolean isRequestingDevice;

		public DeviceResource(DeviceBaseDto device, boolean isRequestingDevice)
		{
			super(device);
			this.isRequestingDevice = isRequestingDevice;
		}

		@Override
		@Nonnull
		public DeviceBaseDto getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		public static void setSslRootCertificateCn(String sslRootCertificateCn)
		{
			DeviceResource.sslRootCertificateCn = sslRootCertificateCn;
		}

		public boolean isRequestingDevice()
		{
			return isRequestingDevice;
		}

		@JsonProperty("firebaseInstanceId")
		@JsonInclude(Include.NON_EMPTY)
		public Optional<String> getFirebaseInstanceId()
		{
			if (getContent() instanceof UserDeviceDto userDeviceDto)
			{
				return userDeviceDto.getFirebaseInstanceId();
			}
			return Optional.empty();
		}

		@JsonProperty("sslRootCertCN")
		@JsonInclude(Include.NON_EMPTY)
		public Optional<String> getSslRootCertCn()
		{
			if (isRequestingDevice)
			{
				return Optional.of(sslRootCertificateCn);
			}
			return Optional.empty();
		}

		@JsonInclude(Include.NON_EMPTY)
		public EntityModel<VPNProfileDto> getVpnProfile()
		{
			if (getContent() instanceof UserDeviceDto userDeviceDto)
			{
				return createVpnProfileResource(userDeviceDto.getVpnProfile());
			}
			return null;
		}

		public static EntityModel<VPNProfileDto> createVpnProfileResource(VPNProfileDto vpnProfileDto)
		{
			EntityModel<VPNProfileDto> vpnProfileResource = EntityModel.of(vpnProfileDto);
			addOvpnProfileLink(vpnProfileResource);
			return vpnProfileResource;
		}

		private static void addOvpnProfileLink(EntityModel<VPNProfileDto> vpnProfileResource)
		{
			vpnProfileResource.add(
					Link.of(ServletUriComponentsBuilder.fromCurrentContextPath().path("/vpn/profile.ovpn").build().toUriString(),
							"ovpnProfile"));
		}
	}

	public static class DeviceResourceAssembler extends RepresentationModelAssemblerSupport<DeviceBaseDto, DeviceResource>
	{
		private final UUID userId;
		private final Optional<UUID> requestingDeviceId;

		public DeviceResourceAssembler(UUID userId, Optional<UUID> requestingDeviceId)
		{
			super(DeviceController.class, DeviceResource.class);
			this.userId = userId;
			this.requestingDeviceId = requestingDeviceId;
		}

		@Override
		public @Nonnull DeviceResource toModel(@Nonnull DeviceBaseDto device)
		{
			DeviceResource deviceResource = instantiateModel(device);
			WebMvcLinkBuilder selfLinkBuilder = getSelfLinkBuilder(device.getId());
			addSelfLink(selfLinkBuilder, deviceResource);
			addEditLink(selfLinkBuilder, deviceResource);
			if (isRequestingDevice(device))
			{
				addPostOpenAppEventLink(deviceResource);
				addAppActivityLink(deviceResource);
				addPostVpnStatusEventLink(deviceResource);
				addSslRootCertificateLink(deviceResource);
				addAppleMobileConfigLink(deviceResource);
			}
			return deviceResource;
		}

		@Override
		protected @Nonnull DeviceResource instantiateModel(@Nonnull DeviceBaseDto device)
		{
			return new DeviceResource(device, isRequestingDevice(device));
		}

		private boolean isRequestingDevice(DeviceBaseDto device)
		{
			return device.getId().equals(requestingDeviceId.orElse(null));
		}

		private WebMvcLinkBuilder getSelfLinkBuilder(UUID deviceId)
		{
			return getDeviceLinkBuilder(userId, deviceId, requestingDeviceId);
		}

		private void addSelfLink(WebMvcLinkBuilder selfLinkBuilder, DeviceResource deviceResource)
		{
			deviceResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(WebMvcLinkBuilder selfLinkBuilder, DeviceResource deviceResource)
		{
			deviceResource.add(selfLinkBuilder.withRel(IanaLinkRelations.EDIT));
		}

		private void addPostOpenAppEventLink(DeviceResource deviceResource)
		{
			deviceResource.add(DeviceController.getPostOpenAppEventLink(userId, deviceResource.getContent().getId()));
		}

		private void addAppActivityLink(DeviceResource deviceResource)
		{
			deviceResource.add(DeviceController.getAppActivityLink(userId, deviceResource.getContent().getId()));
		}

		private void addPostVpnStatusEventLink(DeviceResource deviceResource)
		{
			deviceResource.add(DeviceController.getPostVpnStatusEventLink(userId, deviceResource.getContent().getId()));
		}

		private void addSslRootCertificateLink(DeviceResource deviceResource)
		{
			deviceResource.add(linkTo(methodOn(StandardResourcesController.class).getSslRootCert()).withRel("sslRootCert"));
		}

		private void addAppleMobileConfigLink(DeviceResource deviceResource)
		{
			deviceResource.add(linkTo(methodOn(DeviceController.class).getAppleMobileConfig(Optional.empty(), userId,
					deviceResource.getContent().getId())).withRel("appleMobileConfig"));
		}
	}
}
