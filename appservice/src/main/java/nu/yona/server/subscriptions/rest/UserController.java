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

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.rest.Constants;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

import org.apache.commons.lang.StringUtils;
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
			@RequestParam(value = "tempPassword", required = false) String tempPassword,
			@RequestParam(value = "includePrivateData", defaultValue = "false") String includePrivateDataStr,
			@PathVariable UUID id)
	{
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

	private Optional<String> getPasswordToUse(Optional<String> password, String tempPassword)
	{
		if (password.isPresent())
		{
			return password;
		}
		if (StringUtils.isNotBlank(tempPassword))
		{
			return Optional.of(tempPassword);
		}
		return null;
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

	@RequestMapping(value = "{id}", method = RequestMethod.PUT, params = { "tempPassword" })
	@ResponseBody
	public HttpEntity<UserResource> updateUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "tempPassword", required = false) String tempPassword, @PathVariable UUID id,
			@RequestBody UserDTO userResource)
	{
		if (StringUtils.isBlank(tempPassword))
		{
			return CryptoSession.execute(password, () -> userService.canAccessPrivateData(id),
					() -> createOKResponse(userService.updateUser(id, userResource), true));
		}
		else
		{
			return createOKResponse(userService.updateUserCreatedOnBuddyRequest(id, tempPassword, password.get(), userResource),
					true);
		}
	}

	@RequestMapping(value = "{id}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void deleteUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			userService.deleteUser(password, id);
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

	public static Link getUserLinkWithTempPassword(UUID userID, String tempPassword)
	{
		ControllerLinkBuilder linkBuilder = linkTo(methodOn(UserController.class).updateUser(Optional.empty(), tempPassword,
				userID, null));
		return linkBuilder.withSelfRel();
	}

	public static Link getUserLink(UUID userID, boolean includePrivateData)
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
			return Collections.singletonMap(UserDTO.BUDDIES_REL_NAME, new BuddyController.BuddyResourceAssembler(getContent()
					.getID()).toResources(buddies));
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
			userResource.add(UserController.getUserLink(userResource.getContent().getID(), includePrivateData));
		}
	}
}
