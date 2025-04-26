/*******************************************************************************
 * Copyright (c) 2016, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.RestConstants.NEW_DEVICE_REQUEST_PASSWORD_HEADER;
import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.rest.DeviceController;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.subscriptions.rest.NewDeviceRequestController.NewDeviceRequestResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.DeviceRequestException;
import nu.yona.server.subscriptions.service.NewDeviceRequestDto;
import nu.yona.server.subscriptions.service.NewDeviceRequestService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.subscriptions.service.UserServiceException;

@Controller
@ExposesResourceFor(NewDeviceRequestResource.class)
@RequestMapping(value = "/newDeviceRequests", produces = { MediaType.APPLICATION_JSON_VALUE })
public class NewDeviceRequestController extends ControllerBase
{
	private static final Logger logger = LoggerFactory.getLogger(NewDeviceRequestController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private NewDeviceRequestService newDeviceRequestService;

	@PutMapping(value = "/{mobileNumber}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setNewDeviceRequestForUser(@RequestHeader(value = PASSWORD_HEADER) String password,
			@PathVariable String mobileNumber, @RequestBody NewDeviceRequestCreationDto newDeviceRequestCreation)
	{
		try
		{
			userService.assertValidMobileNumber(mobileNumber);
			UUID userId = userService.getUserByMobileNumber(mobileNumber).getId();
			try (CryptoSession ignored = CryptoSession.start(Optional.of(password),
					() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
			{
				newDeviceRequestService.setNewDeviceRequestForUser(userId, password,
						newDeviceRequestCreation.getNewDeviceRequestPassword());
			}
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists by throwing the same exception
			logger.error("Caught UserServiceException. Mapping it to CryptoException", e);
			throw CryptoException.decryptingData();
		}
	}

	@GetMapping(value = "/{mobileNumber}")
	@ResponseBody
	public HttpEntity<NewDeviceRequestResource> getNewDeviceRequestForUser(
			@RequestHeader(value = NEW_DEVICE_REQUEST_PASSWORD_HEADER) Optional<String> newDeviceRequestPassword,
			@PathVariable String mobileNumber)
	{
		try
		{
			userService.assertValidMobileNumber(mobileNumber);
			UserDto user = userService.getUserByMobileNumber(mobileNumber);
			return createOkResponse(newDeviceRequestService.getNewDeviceRequestForUser(user.getId(), newDeviceRequestPassword),
					createResourceAssembler(user));
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists by throwing the same exception
			logger.error("Caught UserServiceException. Mapping it to DeviceRequestException", e);
			throw DeviceRequestException.noDeviceRequestPresent(mobileNumber);
		}
	}

	@DeleteMapping(value = "/{mobileNumber}")
	@ResponseBody
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clearNewDeviceRequestForUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable String mobileNumber)
	{
		try
		{
			userService.assertValidMobileNumber(mobileNumber);
			UUID userId = userService.getUserByMobileNumber(mobileNumber).getId();
			try (CryptoSession ignored = CryptoSession.start(password,
					() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
			{
				newDeviceRequestService.clearNewDeviceRequestForUser(userId);
			}
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists by throwing the same exception
			logger.error("Caught UserServiceException. Mapping it to CryptoException", e);
			throw CryptoException.decryptingData();
		}
	}

	private NewDeviceRequestResourceAssembler createResourceAssembler(UserDto user)
	{
		return new NewDeviceRequestResourceAssembler(user);
	}

	static WebMvcLinkBuilder getNewDeviceRequestLinkBuilder(String mobileNumber)
	{
		NewDeviceRequestController methodOn = methodOn(NewDeviceRequestController.class);
		return linkTo(methodOn.getNewDeviceRequestForUser(Optional.empty(), mobileNumber));
	}

	public static class NewDeviceRequestResource extends EntityModel<NewDeviceRequestDto>
	{
		public NewDeviceRequestResource(NewDeviceRequestDto newDeviceRequest)
		{
			super(newDeviceRequest);
		}
	}

	public static class NewDeviceRequestResourceAssembler
			extends RepresentationModelAssemblerSupport<NewDeviceRequestDto, NewDeviceRequestResource>
	{
		private final UserDto user;

		public NewDeviceRequestResourceAssembler(UserDto user)
		{
			super(NewDeviceRequestController.class, NewDeviceRequestResource.class);
			this.user = user;
		}

		@Override
		public @Nonnull NewDeviceRequestResource toModel(@Nonnull NewDeviceRequestDto newDeviceRequest)
		{
			NewDeviceRequestResource newDeviceRequestResource = instantiateModel(newDeviceRequest);
			addSelfLink(newDeviceRequestResource);
			addEditLink(newDeviceRequestResource);/* always editable */
			addUserLink(newDeviceRequestResource);
			addRegisterDeviceLink(newDeviceRequestResource);
			return newDeviceRequestResource;
		}

		@Override
		protected @Nonnull NewDeviceRequestResource instantiateModel(@Nonnull NewDeviceRequestDto newDeviceRequest)
		{
			return new NewDeviceRequestResource(newDeviceRequest);
		}

		private void addSelfLink(EntityModel<NewDeviceRequestDto> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(
					NewDeviceRequestController.getNewDeviceRequestLinkBuilder(user.getMobileNumber()).withSelfRel());
		}

		private void addEditLink(EntityModel<NewDeviceRequestDto> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(NewDeviceRequestController.getNewDeviceRequestLinkBuilder(user.getMobileNumber())
					.withRel(IanaLinkRelations.EDIT));
		}

		private void addUserLink(EntityModel<NewDeviceRequestDto> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(UserController.getUserLink(BuddyDto.USER_REL, user.getId(), Optional.empty()));
		}

		private void addRegisterDeviceLink(EntityModel<NewDeviceRequestDto> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(DeviceController.getRegisterDeviceLinkBuilder(user.getId()).withRel("registerDevice"));
		}
	}
}
