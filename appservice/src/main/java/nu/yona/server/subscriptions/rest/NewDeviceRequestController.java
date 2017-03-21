/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
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
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.JsonRootRelProvider;
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
public class NewDeviceRequestController
{
	private static final Logger logger = LoggerFactory.getLogger(NewDeviceRequestController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private NewDeviceRequestService newDeviceRequestService;

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.PUT)
	@ResponseStatus(HttpStatus.OK)
	public void setNewDeviceRequestForUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable String mobileNumber, @RequestBody NewDeviceRequestCreationDto newDeviceRequestCreation)
	{
		try
		{
			userService.assertValidMobileNumber(mobileNumber);
			UUID userId = userService.getUserByMobileNumber(mobileNumber).getId();
			checkPassword(password, userId);
			newDeviceRequestService.setNewDeviceRequestForUser(userId, password.get(),
					newDeviceRequestCreation.getNewDeviceRequestPassword());
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists by throwing the same exception
			logger.error("Caught UserServiceException. Mapping it to CryptoException", e);
			throw CryptoException.decryptingData();
		}
	}

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.GET)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public HttpEntity<NewDeviceRequestResource> getNewDeviceRequestForUser(
			@RequestHeader(value = "Yona-NewDeviceRequestPassword") Optional<String> newDeviceRequestPassword,
			@PathVariable String mobileNumber)
	{
		try
		{
			userService.assertValidMobileNumber(mobileNumber);
			UserDto user = userService.getUserByMobileNumber(mobileNumber);
			return createNewDeviceRequestResponse(user,
					newDeviceRequestService.getNewDeviceRequestForUser(user.getId(), newDeviceRequestPassword), HttpStatus.OK);
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists by throwing the same exception
			logger.error("Caught UserServiceException. Mapping it to DeviceRequestException", e);
			throw DeviceRequestException.noDeviceRequestPresent(mobileNumber);
		}
	}

	@RequestMapping(value = "/{mobileNumber}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void clearNewDeviceRequestForUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable String mobileNumber)
	{
		try
		{
			userService.assertValidMobileNumber(mobileNumber);
			UUID userId = userService.getUserByMobileNumber(mobileNumber).getId();
			checkPassword(password, userId);
			newDeviceRequestService.clearNewDeviceRequestForUser(userId);
		}
		catch (UserServiceException e)
		{
			// prevent detecting whether a mobile number exists by throwing the same exception
			logger.error("Caught UserServiceException. Mapping it to CryptoException", e);
			throw CryptoException.decryptingData();
		}
	}

	private void checkPassword(Optional<String> password, UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			// Nothing to do here. The check is done in the above statement.
		}
	}

	private HttpEntity<NewDeviceRequestResource> createNewDeviceRequestResponse(UserDto user,
			NewDeviceRequestDto newDeviceRequest, HttpStatus statusCode)
	{
		return new ResponseEntity<>(new NewDeviceRequestResourceAssembler(user).toResource(newDeviceRequest), statusCode);
	}

	static ControllerLinkBuilder getNewDeviceRequestLinkBuilder(String mobileNumber)
	{
		NewDeviceRequestController methodOn = methodOn(NewDeviceRequestController.class);
		return linkTo(methodOn.getNewDeviceRequestForUser(null, mobileNumber));
	}

	public static class NewDeviceRequestResource extends Resource<NewDeviceRequestDto>
	{
		public NewDeviceRequestResource(NewDeviceRequestDto newDeviceRequest)
		{
			super(newDeviceRequest);
		}
	}

	public static class NewDeviceRequestResourceAssembler
			extends ResourceAssemblerSupport<NewDeviceRequestDto, NewDeviceRequestResource>
	{
		private final UserDto user;

		public NewDeviceRequestResourceAssembler(UserDto user)
		{
			super(NewDeviceRequestController.class, NewDeviceRequestResource.class);
			this.user = user;
		}

		@Override
		public NewDeviceRequestResource toResource(NewDeviceRequestDto newDeviceRequest)
		{
			NewDeviceRequestResource newDeviceRequestResource = instantiateResource(newDeviceRequest);
			addSelfLink(newDeviceRequestResource);
			addEditLink(newDeviceRequestResource);/* always editable */
			addUserLink(newDeviceRequestResource);
			return newDeviceRequestResource;
		}

		@Override
		protected NewDeviceRequestResource instantiateResource(NewDeviceRequestDto newDeviceRequest)
		{
			return new NewDeviceRequestResource(newDeviceRequest);
		}

		private void addSelfLink(Resource<NewDeviceRequestDto> newDeviceRequestResource)
		{
			newDeviceRequestResource
					.add(NewDeviceRequestController.getNewDeviceRequestLinkBuilder(user.getMobileNumber()).withSelfRel());
		}

		private void addEditLink(Resource<NewDeviceRequestDto> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(NewDeviceRequestController.getNewDeviceRequestLinkBuilder(user.getMobileNumber())
					.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addUserLink(Resource<NewDeviceRequestDto> newDeviceRequestResource)
		{
			newDeviceRequestResource.add(UserController.getPrivateUserLink(BuddyDto.USER_REL_NAME, user.getId()));
		}
	}
}
