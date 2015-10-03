/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
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

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.rest.Constants;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(UserResource.class)
@RequestMapping(value = "/user")
public class UserController {
	@Autowired
	private UserService userService;

	// TODO: Do we need this? It implies a security vulnerability
	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "emailAddress", required = false) String emailAddress,
			@RequestParam(value = "mobileNumber", required = false) String mobileNumber) {

		try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password))) {
			return createOKResponse(userService.getUser(emailAddress, mobileNumber), true);
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<UserResource> getUser(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "full", defaultValue = "false") String fullEntityStr, @PathVariable long id) {
		boolean fullEntity = Boolean.TRUE.toString().equals(fullEntityStr);
		if (fullEntity) {
			try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password))) {
				return createOKResponse(userService.getPrivateUser(id), fullEntity);
			}
		} else {
			return createOKResponse(userService.getPublicUser(id), fullEntity);

		}
	}

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public HttpEntity<UserResource> addUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestBody UserDTO user) {
		try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password))) {
			return createResponse(userService.addUser(user), true, HttpStatus.CREATED);
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<UserResource> updateUser(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable long id,
			@RequestBody UserDTO userResource) {
		try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password))) {
			return createOKResponse(userService.updateUser(id, userResource), true);
		}
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void deleteUser(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable long id) {
		try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password))) {
			userService.deleteUser(password, id);
		}
	}

	public static String getPassword(Optional<String> password) {
		return password.orElseThrow(() -> new YonaException("Missing '" + Constants.PASSWORD_HEADER + "' header"));
	}

	private HttpEntity<UserResource> createResponse(UserDTO user, boolean fullEntity, HttpStatus status) {
		return new ResponseEntity<UserResource>(new UserResourceAssembler(fullEntity).toResource(user), status);
	}

	private HttpEntity<UserResource> createOKResponse(UserDTO user, boolean fullEntity) {
		return createResponse(user, fullEntity, HttpStatus.OK);
	}

	public static Link getUserLink(long userID, boolean fullEntity) {
		return linkTo(
				methodOn(UserController.class).getUser(Optional.empty(), ((Boolean) fullEntity).toString(), userID))
						.withSelfRel();
	}

	static class UserResource extends Resource<UserDTO> {
		public UserResource(UserDTO user) {
			super(user);
		}
	}

	static class UserResourceAssembler extends ResourceAssemblerSupport<UserDTO, UserResource> {
		private final boolean fullEntity;

		public UserResourceAssembler(boolean fullEntity) {
			super(UserController.class, UserResource.class);
			this.fullEntity = fullEntity;
		}

		@Override
		public UserResource toResource(UserDTO user) {
			UserResource userResource = instantiateResource(user);
			addSelfLink(userResource, fullEntity);
			return userResource;
		}

		@Override
		protected UserResource instantiateResource(UserDTO user) {
			return new UserResource(user);
		}

		private static void addSelfLink(Resource<UserDTO> userResource, boolean fullEntity) {
			userResource.add(UserController.getUserLink(userResource.getContent().getID(), fullEntity));
		}
	}
}
