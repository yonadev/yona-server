/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
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
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.subscriptions.rest.BuddyController.BuddyResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(BuddyResource.class)
@RequestMapping(value = "/users/{requestingUserID}/buddies")
public class BuddyController
{
	@Autowired
	private BuddyService buddyService;

	@Autowired
	private UserService userService;

	/**
	 * This method returns all the buddies that the given user has.
	 * 
	 * @param password The Yona password as passed on in the header of the request.
	 * @param requestingUserID The ID of the user. This is part of the URL.
	 * @return the list of buddies for the current user
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<BuddyResource>> getAllBuddies(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserID)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserID),
				() -> new ResponseEntity<Resources<BuddyResource>>(
						createAllBuddiesCollectionResource(requestingUserID, buddyService.getBuddiesOfUser(requestingUserID)),
						HttpStatus.OK));
	}

	@RequestMapping(value = "/{buddyID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<BuddyResource> getBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserID, @PathVariable UUID buddyID)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserID),
				() -> createOKResponse(requestingUserID, buddyService.getBuddy(buddyID)));
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<BuddyResource> addBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserID, @RequestBody BuddyDTO buddy)
	{
		return CryptoSession
				.execute(password, () -> userService.canAccessPrivateData(requestingUserID),
						() -> createResponse(requestingUserID,
								buddyService.addBuddyToRequestingUser(requestingUserID, buddy, this::getInviteURL),
								HttpStatus.CREATED));
	}

	@RequestMapping(value = "/{buddyID}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void removeBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserID, @PathVariable UUID buddyID,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserID), () -> {
			buddyService.removeBuddy(requestingUserID, buddyID, Optional.ofNullable(messageStr));
			return null;
		});
	}

	public static Resources<BuddyResource> createAllBuddiesCollectionResource(UUID userID, Set<BuddyDTO> allBuddiesOfUser)
	{
		return new Resources<>(new BuddyResourceAssembler(userID).toResources(allBuddiesOfUser),
				getAllBuddiesLinkBuilder(userID).withSelfRel());
	}

	static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserID)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllBuddies(null, requestingUserID));
	}

	public String getInviteURL(UUID newUserID, String tempPassword)
	{
		return UserController.getUserSelfLinkWithTempPassword(newUserID, tempPassword).getHref();
	}

	private HttpEntity<BuddyResource> createOKResponse(UUID requestingUserID, BuddyDTO buddy)
	{
		return createResponse(requestingUserID, buddy, HttpStatus.OK);
	}

	private HttpEntity<BuddyResource> createResponse(UUID requestingUserID, BuddyDTO buddy, HttpStatus status)
	{
		return new ResponseEntity<BuddyResource>(new BuddyResourceAssembler(requestingUserID).toResource(buddy), status);
	}

	static ControllerLinkBuilder getBuddyLinkBuilder(UUID userID, UUID buddyID)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getBuddy(Optional.empty(), userID, buddyID));
	}

	static class BuddyResource extends Resource<BuddyDTO>
	{
		public BuddyResource(BuddyDTO buddy)
		{
			super(buddy);
		}

		@JsonProperty("_embedded")
		public Map<String, Object> getEmbeddedResources()
		{
			if (getContent().getUser() == null)
			{
				return Collections.emptyMap();
			}

			HashMap<String, Object> result = new HashMap<String, Object>();
			result.put(BuddyDTO.USER_REL_NAME,
					new UserController.UserResourceAssembler(false).toResource(getContent().getUser()));
			if (getContent().getUser() != null && getContent().getGoals() != null)
			{
				result.put(BuddyDTO.GOALS_REL_NAME,
						GoalController.createAllGoalsCollectionResource(getContent().getUser().getID(), getContent().getGoals()));
			}
			return result;
		}
	}

	static class BuddyResourceAssembler extends ResourceAssemblerSupport<BuddyDTO, BuddyResource>
	{
		private UUID requestingUserID;

		public BuddyResourceAssembler(UUID requestingUserID)
		{
			super(BuddyController.class, BuddyResource.class);
			this.requestingUserID = requestingUserID;
		}

		@Override
		public BuddyResource toResource(BuddyDTO buddy)
		{
			BuddyResource buddyResource = instantiateResource(buddy);
			ControllerLinkBuilder selfLinkBuilder = getSelfLinkBuilder(buddy.getID());
			addSelfLink(selfLinkBuilder, buddyResource);
			return buddyResource;
		}

		@Override
		protected BuddyResource instantiateResource(BuddyDTO buddy)
		{
			return new BuddyResource(buddy);
		}

		private ControllerLinkBuilder getSelfLinkBuilder(UUID buddyID)
		{
			return getBuddyLinkBuilder(requestingUserID, buddyID);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withSelfRel());
		}
	}
}
