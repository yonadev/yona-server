/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.rest.Constants;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.NewDeviceRequestDTO;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/users/")
public class UserController
{
	@Autowired
	private UserService userService;

	@Autowired
	private BuddyService buddyService;

	@RequestMapping(value = "{id}", params = { "includePrivateData" }, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr,
			@RequestParam(value = "includePrivateData", defaultValue = "false") String includePrivateDataStr,
			@PathVariable UUID id)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		Optional<String> passwordToUse = getPasswordToUse(password, tempPassword);
		boolean includePrivateData = Boolean.TRUE.toString().equals(includePrivateDataStr);
		if (includePrivateData)
		{
			return CryptoSession.execute(passwordToUse, () -> userService.canAccessPrivateData(id),
					() -> createOKResponse(userService.getPrivateUser(id), includePrivateData));
		}
		else
		{
			return getPublicUser(passwordToUse, id);
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

	@RequestMapping(value = "{id}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getPublicUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID id)
	{
		return createOKResponse(userService.getPublicUser(id), false);
	}

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public HttpEntity<UserResource> addUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestBody UserDTO user)
	{
		return CryptoSession.execute(password, () -> createResponse(userService.addUser(user), true, HttpStatus.CREATED));
	}

	private void checkPassword(Optional<String> password, UUID userID)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> null);
	}

	@RequestMapping(value = "{userID}/confirm", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<UserResource> addUserConfimation(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@RequestBody UserSignInConfirmationDTO newDeviceRequestCreation)
	{
		checkPassword(password, userID);
		userService.confirmSignIn(userID, newDeviceRequestCreation.getCode());
		return createOKResponse(userService.getPublicUser(userID), false);
	}

	@RequestMapping(value = "{userID}/newDeviceRequest", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<NewDeviceRequestResource> setNewDeviceRequestForUser(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@RequestBody NewDeviceRequestCreationDTO newDeviceRequestCreation)
	{
		checkPassword(password, userID);
		NewDeviceRequestDTO newDeviceRequestResult = userService.setNewDeviceRequestForUser(userID, password.get(),
				newDeviceRequestCreation.getUserSecret());
		return createNewDeviceRequestResponse(newDeviceRequestResult, getNewDeviceRequestLinkBuilder(userID),
				newDeviceRequestResult.getIsUpdatingExistingRequest() ? HttpStatus.OK : HttpStatus.CREATED);
	}

	@RequestMapping(value = "{userID}/newDeviceRequest", params = { "userSecret" }, method = RequestMethod.GET)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public HttpEntity<NewDeviceRequestResource> getNewDeviceRequestForUser(@PathVariable UUID userID,
			@RequestParam(value = "userSecret", required = false) String userSecret)
	{
		return createNewDeviceRequestResponse(userService.getNewDeviceRequestForUser(userID, userSecret),
				getNewDeviceRequestLinkBuilder(userID), HttpStatus.OK);
	}

	@RequestMapping(value = "{userID}/newDeviceRequest", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void clearNewDeviceRequestForUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID)
	{
		checkPassword(password, userID);
		userService.clearNewDeviceRequestForUser(userID);
	}

	static ControllerLinkBuilder getNewDeviceRequestLinkBuilder(UUID userID)
	{
		UserController methodOn = methodOn(UserController.class);
		return linkTo(methodOn.getNewDeviceRequestForUser(userID, null));
	}

	private HttpEntity<NewDeviceRequestResource> createNewDeviceRequestResponse(NewDeviceRequestDTO newDeviceRequest,
			ControllerLinkBuilder entityLinkBuilder, HttpStatus statusCode)
	{
		return new ResponseEntity<NewDeviceRequestResource>(new NewDeviceRequestResource(newDeviceRequest, entityLinkBuilder),
				statusCode);
	}

	public static class NewDeviceRequestResource extends Resource<NewDeviceRequestDTO>
	{
		public NewDeviceRequestResource(NewDeviceRequestDTO newDeviceRequest, ControllerLinkBuilder entityLinkBuilder)
		{
			super(newDeviceRequest, entityLinkBuilder.withSelfRel());
		}
	}

	@RequestMapping(value = "{id}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<UserResource> updateUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPasswordStr, @PathVariable UUID id,
			@RequestBody UserDTO userResource)
	{
		Optional<String> tempPassword = Optional.ofNullable(tempPasswordStr);
		if (tempPassword.isPresent())
		{
			return CryptoSession.execute(password, null,
					() -> createOKResponse(userService.updateUserCreatedOnBuddyRequest(id, tempPassword.get(), userResource),
							true));
		}
		else
		{
			return CryptoSession.execute(password, () -> userService.canAccessPrivateData(id),
					() -> createOKResponse(userService.updateUser(id, userResource), true));
		}
	}

	@RequestMapping(value = "{id}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void deleteUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			userService.deleteUser(id, Optional.ofNullable(messageStr));
			return null;
		});
	}

	private HttpEntity<UserResource> createResponse(UserDTO user, boolean includePrivateData, HttpStatus status)
	{
		if (includePrivateData)
		{
			Set<BuddyDTO> buddies = buddyService.getBuddiesOfUser(user.getID());
			user.getPrivateData().setBuddies(buddies);
		}
		return new ResponseEntity<UserResource>(new UserResourceAssembler(includePrivateData).toResource(user), status);
	}

	private HttpEntity<UserResource> createOKResponse(UserDTO user, boolean includePrivateData)
	{
		return createResponse(user, includePrivateData, HttpStatus.OK);
	}

	static Link getUserSelfLinkWithTempPassword(UUID userID, String tempPassword)
	{
		ControllerLinkBuilder linkBuilder = linkTo(
				methodOn(UserController.class).updateUser(Optional.empty(), tempPassword, userID, null));
		return linkBuilder.withSelfRel();
	}

	private static Link getUserSelfLink(UUID userID, boolean includePrivateData)
	{
		ControllerLinkBuilder linkBuilder;
		if (includePrivateData)
		{
			linkBuilder = linkTo(methodOn(UserController.class).getUser(Optional.empty(), null, Boolean.TRUE.toString(), userID));
		}
		else
		{
			linkBuilder = linkTo(methodOn(UserController.class).getPublicUser(Optional.empty(), userID));
		}
		return linkBuilder.withSelfRel();
	}

	static class UserResource extends Resource<UserDTO>
	{
		public UserResource(UserDTO user)
		{
			super(user);
		}

		@JsonProperty("_embedded")
		public Map<String, List<BuddyController.BuddyResource>> getEmbeddedResources()
		{
			if (getContent().getPrivateData() == null)
			{
				return Collections.emptyMap();
			}

			Set<BuddyDTO> buddies = getContent().getPrivateData().getBuddies();
			return Collections.singletonMap(UserDTO.BUDDIES_REL_NAME,
					new BuddyController.BuddyResourceAssembler(getContent().getID()).toResources(buddies));
		}

		static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserID)
		{
			BuddyController methodOn = methodOn(BuddyController.class);
			return linkTo(methodOn.getAllBuddies(null, requestingUserID));
		}
	}

	static class UserResourceAssembler extends ResourceAssemblerSupport<UserDTO, UserResource>
	{
		private final boolean includePrivateData;

		public UserResourceAssembler(boolean includePrivateData)
		{
			super(UserController.class, UserResource.class);
			this.includePrivateData = includePrivateData;
		}

		@Override
		public UserResource toResource(UserDTO user)
		{
			UserResource userResource = instantiateResource(user);
			addSelfLink(userResource, includePrivateData);
			return userResource;
		}

		@Override
		protected UserResource instantiateResource(UserDTO user)
		{
			return new UserResource(user);
		}

		private static void addSelfLink(Resource<UserDTO> userResource, boolean includePrivateData)
		{
			userResource.add(UserController.getUserSelfLink(userResource.getContent().getID(), includePrivateData));
		}
	}
}
