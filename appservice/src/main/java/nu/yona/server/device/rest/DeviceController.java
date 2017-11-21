/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.rest.DeviceController.DeviceResource;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(DeviceResource.class)
@RequestMapping(value = "/users/{userId}/devices", produces = { MediaType.APPLICATION_JSON_VALUE })
public class DeviceController extends ControllerBase
{
	@Autowired
	protected CurieProvider curieProvider;

	@Autowired
	private DeviceService deviceService;

	@Autowired
	private UserService userService;

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

	public static Resources<DeviceResource> createAllDevicesCollectionResource(CurieProvider curieProvider, UUID userId,
			Set<DeviceBaseDto> devices)
	{
		return new Resources<>(new DeviceResourceAssembler(curieProvider, userId).toResources(devices),
				DeviceController.getAllDevicesLinkBuilder(userId).withSelfRel());
	}

	public static class DeviceResource extends Resource<DeviceBaseDto>
	{
		private final CurieProvider curieProvider;
		private final UUID userId;

		public DeviceResource(CurieProvider curieProvider, UUID userId, DeviceBaseDto device)
		{
			super(device);
			this.curieProvider = curieProvider;
			this.userId = userId;
		}
	}

	public static class DeviceResourceAssembler extends ResourceAssemblerSupport<DeviceBaseDto, DeviceResource>
	{
		private final UUID userId;
		private final CurieProvider curieProvider;

		public DeviceResourceAssembler(CurieProvider curieProvider, UUID userId)
		{
			super(DeviceController.class, DeviceResource.class);
			this.curieProvider = curieProvider;
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
			return new DeviceResource(curieProvider, userId, device);
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
