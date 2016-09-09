/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.hal.CurieProvider;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.rest.BuddyActivityController;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.rest.GoalController.GoalResourceAssembler;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalServiceException;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.rest.BuddyController.BuddyResource;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.BuddyServiceException;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(BuddyResource.class)
@RequestMapping(value = "/users/{requestingUserID}/buddies", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BuddyController
{
	public static final String BUDDY_LINK = "buddy";

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurieProvider curieProvider;

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
				() -> new ResponseEntity<Resources<BuddyResource>>(createAllBuddiesCollectionResource(curieProvider,
						requestingUserID, buddyService.getBuddiesOfUser(requestingUserID)), HttpStatus.OK));
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
			@PathVariable UUID requestingUserID, @RequestBody PostPutBuddyDTO postPutBuddy)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserID),
				() -> createResponse(requestingUserID,
						buddyService.addBuddyToRequestingUser(requestingUserID, convertToBuddy(postPutBuddy), this::getInviteURL),
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

	@RequestMapping(value = "/{buddyID}/goals/{goalID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalDTO> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserID, @PathVariable UUID buddyID, @PathVariable UUID goalID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserID),
				() -> createResponse(requestingUserID, getGoal(requestingUserID, buddyID, goalID), HttpStatus.OK));
	}

	@RequestMapping(value = "/{buddyID}/goals/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<GoalDTO>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserID, @PathVariable UUID buddyID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserID),
				() -> new ResponseEntity<Resources<GoalDTO>>(
						createAllGoalsCollectionResource(requestingUserID, buddyID, getGoals(requestingUserID, buddyID)),
						HttpStatus.OK));
	}

	private GoalDTO getGoal(UUID requestingUserID, UUID buddyID, UUID goalID)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyID);
		Optional<GoalDTO> goal = buddy.getGoals().stream().filter(g -> g.getID().equals(goalID)).findAny();
		return goal.orElseThrow(() -> GoalServiceException.goalNotFoundByIdForBuddy(requestingUserID, buddyID, goalID));
	}

	private Set<GoalDTO> getGoals(UUID requestingUserID, UUID buddyID)
	{
		return buddyService.getBuddy(buddyID).getGoals();
	}

	private BuddyDTO convertToBuddy(PostPutBuddyDTO postPutBuddy)
	{
		String userRelName = curieProvider.getNamespacedRelFor(BuddyDTO.USER_REL_NAME);
		UserDTO user = postPutBuddy.userInMap.get(userRelName);
		if (user == null)
		{
			throw BuddyServiceException.missingUser(userRelName);
		}
		return new BuddyDTO(user, postPutBuddy.message, postPutBuddy.sendingStatus, postPutBuddy.receivingStatus);
	}

	public static Resources<BuddyResource> createAllBuddiesCollectionResource(CurieProvider curieProvider, UUID userID,
			Set<BuddyDTO> allBuddiesOfUser)
	{
		return new Resources<>(new BuddyResourceAssembler(curieProvider, userID).toResources(allBuddiesOfUser),
				getAllBuddiesLinkBuilder(userID).withSelfRel());
	}

	static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserID)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllBuddies(null, requestingUserID));
	}

	public String getInviteURL(UUID newUserID, String tempPassword)
	{
		return UserController.getBuddyInvitationDeepLinkLandingPageLink(newUserID, tempPassword).toUri().toString();
	}

	private HttpEntity<BuddyResource> createOKResponse(UUID requestingUserID, BuddyDTO buddy)
	{
		return createResponse(requestingUserID, buddy, HttpStatus.OK);
	}

	private HttpEntity<BuddyResource> createResponse(UUID requestingUserID, BuddyDTO buddy, HttpStatus status)
	{
		return new ResponseEntity<BuddyResource>(new BuddyResourceAssembler(curieProvider, requestingUserID).toResource(buddy),
				status);
	}

	private HttpEntity<GoalDTO> createResponse(UUID userID, GoalDTO goal, HttpStatus status)
	{
		return new ResponseEntity<GoalDTO>(new GoalResourceAssembler(userID).toResource(goal), status);
	}

	public static Resources<GoalDTO> createAllGoalsCollectionResource(UUID userID, UUID buddyID, Set<GoalDTO> allGoalsOfUser)
	{
		return new Resources<>(new GoalResourceAssembler(true, (goalID) -> getGoalLinkBuilder(userID, buddyID, goalID))
				.toResources(allGoalsOfUser), getAllGoalsLinkBuilder(userID, buddyID).withSelfRel());
	}

	public static ControllerLinkBuilder getBuddyLinkBuilder(UUID userID, UUID buddyID)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getBuddy(Optional.empty(), userID, buddyID));
	}

	public static ControllerLinkBuilder getGoalLinkBuilder(UUID userID, UUID buddyID, UUID goalID)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getGoal(Optional.empty(), userID, buddyID, goalID));
	}

	public static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID userID, UUID buddyID)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllGoals(Optional.empty(), userID, buddyID));
	}

	static class PostPutBuddyDTO
	{
		private final Map<String, UserDTO> userInMap;
		private final String message;
		private final Status sendingStatus;
		private final Status receivingStatus;

		@JsonCreator
		public PostPutBuddyDTO(@JsonProperty(value = "_embedded", required = true) Map<String, UserDTO> userInMap,
				@JsonProperty("message") String message,
				@JsonProperty(value = "sendingStatus", required = true) Status sendingStatus,
				@JsonProperty(value = "receivingStatus", required = true) Status receivingStatus)
		{
			this.userInMap = userInMap;
			this.message = message;
			this.sendingStatus = sendingStatus;
			this.receivingStatus = receivingStatus;
		}
	}

	static class BuddyResource extends Resource<BuddyDTO>
	{
		private final CurieProvider curieProvider;
		private final UUID requestingUserID;

		public BuddyResource(CurieProvider curieProvider, UUID requestingUserID, BuddyDTO buddy)
		{
			super(buddy);
			this.curieProvider = curieProvider;
			this.requestingUserID = requestingUserID;
		}

		@JsonProperty("_embedded")
		public Map<String, Object> getEmbeddedResources()
		{
			if (getContent().getUser() == null)
			{
				return Collections.emptyMap();
			}

			HashMap<String, Object> result = new HashMap<String, Object>();
			result.put(curieProvider.getNamespacedRelFor(BuddyDTO.USER_REL_NAME),
					new UserController.UserResourceAssembler(curieProvider, false).toResource(getContent().getUser()));
			if (getContent().getUser() != null && getContent().getGoals() != null)
			{
				result.put(curieProvider.getNamespacedRelFor(BuddyDTO.GOALS_REL_NAME), BuddyController
						.createAllGoalsCollectionResource(requestingUserID, getContent().getID(), getContent().getGoals()));
			}
			return result;
		}
	}

	static class BuddyResourceAssembler extends ResourceAssemblerSupport<BuddyDTO, BuddyResource>
	{
		private final UUID requestingUserID;
		private final CurieProvider curieProvider;

		public BuddyResourceAssembler(CurieProvider curieProvider, UUID requestingUserID)
		{
			super(BuddyController.class, BuddyResource.class);
			this.curieProvider = curieProvider;
			this.requestingUserID = requestingUserID;
		}

		@Override
		public BuddyResource toResource(BuddyDTO buddy)
		{
			BuddyResource buddyResource = instantiateResource(buddy);
			ControllerLinkBuilder selfLinkBuilder = getSelfLinkBuilder(buddy.getID());
			addSelfLink(selfLinkBuilder, buddyResource);
			addEditLink(selfLinkBuilder, buddyResource);
			if (buddy.getSendingStatus() == Status.ACCEPTED)
			{
				addDayActivityOverviewsLink(buddyResource);
				addWeekActivityOverviewsLink(buddyResource);
			}
			return buddyResource;
		}

		@Override
		protected BuddyResource instantiateResource(BuddyDTO buddy)
		{
			return new BuddyResource(curieProvider, requestingUserID, buddy);
		}

		private ControllerLinkBuilder getSelfLinkBuilder(UUID buddyID)
		{
			return getBuddyLinkBuilder(requestingUserID, buddyID);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addWeekActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(BuddyActivityController
					.getBuddyWeekActivityOverviewsLinkBuilder(requestingUserID, buddyResource.getContent().getID())
					.withRel(BuddyActivityController.WEEK_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(BuddyActivityController
					.getBuddyDayActivityOverviewsLinkBuilder(requestingUserID, buddyResource.getContent().getID())
					.withRel(BuddyActivityController.DAY_OVERVIEW_LINK));
		}
	}
}
