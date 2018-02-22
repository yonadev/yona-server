/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.service.AnalysisEngineProxyService;
import nu.yona.server.analysis.service.AppActivityDto;
import nu.yona.server.analysis.service.AppActivityDto.Activity;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.rest.DeviceController.DeviceResource;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.device.service.DeviceServiceException;
import nu.yona.server.device.service.UserDeviceDto;
import nu.yona.server.device.service.UserDeviceDto.DeviceRegistrationRequestDto;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.rest.AppleMobileConfigSigner;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.NewDeviceRequestDto;
import nu.yona.server.subscriptions.service.NewDeviceRequestService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.VPNProfileDto;
import nu.yona.server.util.ThymeleafUtil;

@Controller
@ExposesResourceFor(DeviceResource.class)
@RequestMapping(value = "/users/{userId}/devices", produces = { MediaType.APPLICATION_JSON_VALUE })
public class DeviceController extends ControllerBase
{
	private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

	@Autowired
	protected CurieProvider curieProvider;

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

	private enum MessageType
	{
		ERROR, WARNING
	}

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<DeviceResource>> getAllDevices(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return createOkResponse(deviceService.getDevicesOfUser(userId), createResourceAssembler(userId),
					getAllDevicesLinkBuilder(userId));
		}
	}

	@RequestMapping(value = "/{deviceId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<DeviceResource> getDevice(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return createOkResponse(deviceService.getDevice(deviceId), createResourceAssembler(userId));
		}
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<UserResource> registerDevice(
			@RequestHeader(value = Constants.NEW_DEVICE_REQUEST_PASSWORD_HEADER) String newDeviceRequestPassword,
			@PathVariable UUID userId, @RequestBody DeviceRegistrationRequestDto request)
	{
		NewDeviceRequestDto newDeviceRequest = newDeviceRequestService.getNewDeviceRequestForUser(userId,
				Optional.of(newDeviceRequestPassword));
		try (CryptoSession cryptoSession = CryptoSession.start(newDeviceRequest.getYonaPassword(),
				() -> userService.canAccessPrivateData(userId)))
		{
			UserDeviceDto newDevice = deviceService.addDeviceToUser(userId,
					UserDeviceDto.createDeviceRegistrationInstance(request));
			return createResponse(userService.getPrivateUser(userId, false), HttpStatus.CREATED,
					userController.createResourceAssemblerForOwnUser(userId, Optional.of(newDevice.getId())));
		}
	}

	@RequestMapping(value = "/{deviceId}/openApp", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> postOpenAppEvent(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId, @RequestBody AppOpenEventDto request)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			deviceService.postOpenAppEvent(userId, deviceId, request.getOperatingSystem(),
					Optional.ofNullable(request.appVersion));
			return createOkResponse();
		}
	}

	@RequestMapping(value = "/{deviceId}/apple.mobileconfig", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> getAppleMobileConfig(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID deviceId)
	{
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "x-apple-aspen-config"));
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(getDeviceSpecificAppleMobileConfig(deviceService.getDevice(deviceId)), headers,
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

	private byte[] signIfEnabled(byte[] unsignedMobileconfig)
	{
		if (yonaProperties.getAppleMobileConfig().isSigningEnabled())
		{
			return appleMobileConfigSigner.sign(unsignedMobileconfig);
		}
		return unsignedMobileconfig;
	}

	/*
	 * Adds app activity registered by the Yona app. This request is delegated to the analysis engine service.
	 * @param password User password, validated before adding the activity.
	 * @param appActivities Because it may be that multiple app activities may have taken place during the time the network is
	 * down, accept an array of activities.
	 */
	@RequestMapping(value = "/{deviceId}/appActivity/", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> addAppActivity(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID deviceId, @RequestBody AppActivityDto appActivities)
	{
		if (appActivities.getActivities().length > yonaProperties.getAnalysisService().getAppActivityCountIgnoreThreshold())
		{
			logLongAppActivityBatch(MessageType.ERROR, userId, appActivities);
			return createOkResponse();
		}
		if (appActivities.getActivities().length > yonaProperties.getAnalysisService().getAppActivityCountLoggingThreshold())
		{
			logLongAppActivityBatch(MessageType.WARNING, userId, appActivities);
		}
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			UserDto userDto = userService.getPrivateUser(userId);
			autoregisterAndroid(userDto, deviceId);
			UUID userAnonymizedId = userDto.getOwnPrivateData().getUserAnonymizedId();
			UUID deviceAnonymizedId = deviceService.getDeviceAnonymizedId(userDto, deviceId);
			analysisEngineProxyService.analyzeAppActivity(userAnonymizedId, deviceAnonymizedId, appActivities);
			return createOkResponse();
		}
	}

	/**
	 * Autoregisters the given device as running Android. As of today, only the Android app is capable of sending app activities,
	 * so if the operating system of the requesting device is marked as UNKNOWN, we can improve the registration and mark it as
	 * ANDROID.
	 * 
	 * @param userDto The user sending the app activities
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

	private void logLongAppActivityBatch(MessageType messageType, UUID userId, AppActivityDto appActivities)
	{
		int numAppActivities = appActivities.getActivities().length;
		List<Activity> appActivityCollection = Arrays.asList(appActivities.getActivities());
		Comparator<? super Activity> comparator = (a, b) -> a.getStartTime().compareTo(b.getStartTime());
		ZonedDateTime minStartTime = Collections.min(appActivityCollection, comparator).getStartTime();
		ZonedDateTime maxStartTime = Collections.max(appActivityCollection, comparator).getStartTime();
		switch (messageType)
		{
			case ERROR:
				logger.error(
						"User with ID {} posts too many ({}) app activities, with start dates ranging from {} to {} (device time: {}). App activities ignored.",
						userId, numAppActivities, minStartTime, maxStartTime, appActivities.getDeviceDateTime());
				break;
			case WARNING:
				logger.warn(
						"User with ID {} posts many ({}) app activities, with start dates ranging from {} to {} (device time: {})",
						userId, numAppActivities, minStartTime, maxStartTime, appActivities.getDeviceDateTime());
				break;
			default:
				throw new IllegalStateException("Unsupported message type: " + messageType);
		}
	}

	public static Link getPostOpenAppEventLink(UUID userId, UUID deviceId)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(DeviceController.class).postOpenAppEvent(Optional.empty(), userId, deviceId, null));
		return linkBuilder.withRel("postOpenAppEvent");
	}

	public static Link getAppActivityLink(UUID userId, UUID deviceId)
	{
		try
		{
			ControllerLinkBuilder linkBuilder = linkTo(
					methodOn(DeviceController.class).addAppActivity(Optional.empty(), userId, deviceId, null));
			return linkBuilder.withRel("appActivity");
		}
		catch (SecurityException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private DeviceResourceAssembler createResourceAssembler(UUID userId)
	{
		return new DeviceResourceAssembler(curieProvider, userId);
	}

	public static ControllerLinkBuilder getAllDevicesLinkBuilder(UUID userId)
	{
		DeviceController methodOn = methodOn(DeviceController.class);
		return linkTo(methodOn.getAllDevices(null, userId));
	}

	public static ControllerLinkBuilder getDeviceLinkBuilder(UUID userId, UUID deviceId)
	{
		DeviceController methodOn = methodOn(DeviceController.class);
		return linkTo(methodOn.getDevice(Optional.empty(), userId, deviceId));
	}

	public static ControllerLinkBuilder getRegisterDeviceLinkBuilder(UUID userId)
	{
		DeviceController methodOn = methodOn(DeviceController.class);
		return linkTo(methodOn.registerDevice(null, userId, null));
	}

	public static Resources<DeviceResource> createAllDevicesCollectionResource(CurieProvider curieProvider, UUID userId,
			Set<DeviceBaseDto> devices)
	{
		return new Resources<>(new DeviceResourceAssembler(curieProvider, userId).toResources(devices),
				DeviceController.getAllDevicesLinkBuilder(userId).withSelfRel());
	}

	static class AppOpenEventDto
	{
		private final String operatingSystemStr;
		private final String appVersion;

		@JsonCreator
		AppOpenEventDto(@JsonProperty("operatingSystem") String operatingSystemStr, @JsonProperty("appVersion") String appVersion)
		{
			this.operatingSystemStr = operatingSystemStr;
			this.appVersion = appVersion;
		}

		Optional<OperatingSystem> getOperatingSystem()
		{
			return operatingSystemStr == null ? Optional.empty()
					: Optional.of(UserDeviceDto.parseOperatingSystemOfRegistrationRequest(operatingSystemStr));
		}
	}

	public static class DeviceResource extends Resource<DeviceBaseDto>
	{
		public DeviceResource(DeviceBaseDto device)
		{
			super(device);
		}

		@JsonInclude(Include.NON_EMPTY)
		public Resource<VPNProfileDto> getVpnProfile()
		{
			if (getContent() instanceof UserDeviceDto)
			{
				return createVpnProfileResource(((UserDeviceDto) getContent()).getVpnProfile());
			}
			return null;
		}

		public static Resource<VPNProfileDto> createVpnProfileResource(VPNProfileDto vpnProfileDto)
		{
			Resource<VPNProfileDto> vpnProfileResource = new Resource<>(vpnProfileDto);
			addOvpnProfileLink(vpnProfileResource);
			return vpnProfileResource;
		}

		private static void addOvpnProfileLink(Resource<VPNProfileDto> vpnProfileResource)
		{
			vpnProfileResource.add(
					new Link(ServletUriComponentsBuilder.fromCurrentContextPath().path("/vpn/profile.ovpn").build().toUriString(),
							"ovpnProfile"));
		}
	}

	public static class DeviceResourceAssembler extends ResourceAssemblerSupport<DeviceBaseDto, DeviceResource>
	{
		private final UUID userId;

		public DeviceResourceAssembler(CurieProvider curieProvider, UUID userId)
		{
			super(DeviceController.class, DeviceResource.class);
			this.userId = userId;
		}

		@Override
		public DeviceResource toResource(DeviceBaseDto device)
		{
			DeviceResource deviceResource = instantiateResource(device);
			ControllerLinkBuilder selfLinkBuilder = getSelfLinkBuilder(device.getId());
			addSelfLink(selfLinkBuilder, deviceResource);
			addEditLink(selfLinkBuilder, deviceResource);
			return deviceResource;
		}

		@Override
		protected DeviceResource instantiateResource(DeviceBaseDto device)
		{
			return new DeviceResource(device);
		}

		private ControllerLinkBuilder getSelfLinkBuilder(UUID deviceId)
		{
			return getDeviceLinkBuilder(userId, deviceId);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, DeviceResource deviceResource)
		{
			deviceResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, DeviceResource deviceResource)
		{
			deviceResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}
	}
}
