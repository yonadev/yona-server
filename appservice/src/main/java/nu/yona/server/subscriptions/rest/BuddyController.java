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
import nu.yona.server.util.TimeUtil;

@Controller
@ExposesResourceFor(BuddyResource.class)
@RequestMapping(value = "/users/{requestingUserId}/buddies", produces = { MediaType.APPLICATION_JSON_VALUE })
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
	 * @param requestingUserId The ID of the user. This is part of the URL.
	 * @return the list of buddies for the current user
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<BuddyResource>> getAllBuddies(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserId)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserId),
				() -> new ResponseEntity<Resources<BuddyResource>>(createAllBuddiesCollectionResource(curieProvider,
						requestingUserId, buddyService.getBuddiesOfUser(requestingUserId)), HttpStatus.OK));
	}

	@RequestMapping(value = "/{buddyId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<BuddyResource> getBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserId, @PathVariable UUID buddyId)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserId),
				() -> createOkResponse(requestingUserId, buddyService.getBuddy(buddyId)));
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<BuddyResource> addBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserId, @RequestBody PostPutBuddyDTO postPutBuddy)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserId),
				() -> createResponse(requestingUserId,
						buddyService.addBuddyToRequestingUser(requestingUserId, convertToBuddy(postPutBuddy), this::getInviteUrl),
						HttpStatus.CREATED));
	}

	@RequestMapping(value = "/{buddyId}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void removeBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserId, @PathVariable UUID buddyId,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserId), () -> {
			buddyService.removeBuddy(requestingUserId, buddyId, Optional.ofNullable(messageStr));
			return null;
		});
	}

	@RequestMapping(value = "/{buddyId}/goals/{goalId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalDTO> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserId, @PathVariable UUID buddyId, @PathVariable UUID goalId)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserId),
				() -> createResponse(requestingUserId, getGoal(requestingUserId, buddyId, goalId), HttpStatus.OK));
	}

	@RequestMapping(value = "/{buddyId}/goals/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<GoalDTO>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID requestingUserId, @PathVariable UUID buddyId)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(requestingUserId),
				() -> new ResponseEntity<Resources<GoalDTO>>(
						createAllGoalsCollectionResource(requestingUserId, buddyId, getGoals(requestingUserId, buddyId)),
						HttpStatus.OK));
	}

	private GoalDTO getGoal(UUID requestingUserId, UUID buddyId, UUID goalId)
	{
		BuddyDTO buddy = buddyService.getBuddy(buddyId);
		Optional<GoalDTO> goal = buddy.getGoals().stream().filter(g -> g.getGoalId().equals(goalId)).findAny();
		return goal.orElseThrow(() -> GoalServiceException.goalNotFoundByIdForBuddy(requestingUserId, buddyId, goalId));
	}

	private Set<GoalDTO> getGoals(UUID requestingUserId, UUID buddyId)
	{
		return buddyService.getBuddy(buddyId).getGoals();
	}

	private BuddyDTO convertToBuddy(PostPutBuddyDTO postPutBuddy)
	{
		String userRelName = curieProvider.getNamespacedRelFor(BuddyDTO.USER_REL_NAME);
		UserDTO user = postPutBuddy.userInMap.get(userRelName);
		if (user == null)
		{
			throw BuddyServiceException.missingUser(userRelName);
		}
		return new BuddyDTO(user, postPutBuddy.message, postPutBuddy.sendingStatus, postPutBuddy.receivingStatus,
				TimeUtil.utcNow());
	}

	public static Resources<BuddyResource> createAllBuddiesCollectionResource(CurieProvider curieProvider, UUID userId,
			Set<BuddyDTO> allBuddiesOfUser)
	{
		return new Resources<>(new BuddyResourceAssembler(curieProvider, userId).toResources(allBuddiesOfUser),
				getAllBuddiesLinkBuilder(userId).withSelfRel());
	}

	static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID requestingUserId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllBuddies(null, requestingUserId));
	}

	public String getInviteUrl(UUID newUserId, String tempPassword)
	{
		return UserController.getUserSelfLinkWithTempPassword(newUserId, tempPassword).getHref();
	}

	private HttpEntity<BuddyResource> createOkResponse(UUID requestingUserId, BuddyDTO buddy)
	{
		return createResponse(requestingUserId, buddy, HttpStatus.OK);
	}

	private HttpEntity<BuddyResource> createResponse(UUID requestingUserId, BuddyDTO buddy, HttpStatus status)
	{
		return new ResponseEntity<BuddyResource>(new BuddyResourceAssembler(curieProvider, requestingUserId).toResource(buddy),
				status);
	}

	private HttpEntity<GoalDTO> createResponse(UUID userId, GoalDTO goal, HttpStatus status)
	{
		return new ResponseEntity<GoalDTO>(new GoalResourceAssembler(userId).toResource(goal), status);
	}

	public static Resources<GoalDTO> createAllGoalsCollectionResource(UUID userId, UUID buddyId, Set<GoalDTO> allGoalsOfUser)
	{
		return new Resources<>(new GoalResourceAssembler(true, (goalId) -> getGoalLinkBuilder(userId, buddyId, goalId))
				.toResources(allGoalsOfUser), getAllGoalsLinkBuilder(userId, buddyId).withSelfRel());
	}

	public static ControllerLinkBuilder getBuddyLinkBuilder(UUID userId, UUID buddyId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getBuddy(Optional.empty(), userId, buddyId));
	}

	public static ControllerLinkBuilder getGoalLinkBuilder(UUID userId, UUID buddyId, UUID goalId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getGoal(Optional.empty(), userId, buddyId, goalId));
	}

	public static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID userId, UUID buddyId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllGoals(Optional.empty(), userId, buddyId));
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
		private final UUID requestingUserId;

		public BuddyResource(CurieProvider curieProvider, UUID requestingUserId, BuddyDTO buddy)
		{
			super(buddy);
			this.curieProvider = curieProvider;
			this.requestingUserId = requestingUserId;
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
						.createAllGoalsCollectionResource(requestingUserId, getContent().getId(), getContent().getGoals()));
			}
			return result;
		}
	}

	static class BuddyResourceAssembler extends ResourceAssemblerSupport<BuddyDTO, BuddyResource>
	{
		private final UUID requestingUserId;
		private final CurieProvider curieProvider;

		public BuddyResourceAssembler(CurieProvider curieProvider, UUID requestingUserId)
		{
			super(BuddyController.class, BuddyResource.class);
			this.curieProvider = curieProvider;
			this.requestingUserId = requestingUserId;
		}

		@Override
		public BuddyResource toResource(BuddyDTO buddy)
		{
			BuddyResource buddyResource = instantiateResource(buddy);
			ControllerLinkBuilder selfLinkBuilder = getSelfLinkBuilder(buddy.getId());
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
			return new BuddyResource(curieProvider, requestingUserId, buddy);
		}

		private ControllerLinkBuilder getSelfLinkBuilder(UUID buddyId)
		{
			return getBuddyLinkBuilder(requestingUserId, buddyId);
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
					.getBuddyWeekActivityOverviewsLinkBuilder(requestingUserId, buddyResource.getContent().getId())
					.withRel(BuddyActivityController.WEEK_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(BuddyActivityController
					.getBuddyDayActivityOverviewsLinkBuilder(requestingUserId, buddyResource.getContent().getId())
					.withRel(BuddyActivityController.DAY_OVERVIEW_LINK));
		}
	}
}
