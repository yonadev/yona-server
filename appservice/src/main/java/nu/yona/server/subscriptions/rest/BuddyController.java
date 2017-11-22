/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.goals.rest.GoalController.GoalResourceAssembler;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalServiceException;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.rest.BuddyController.BuddyResource;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.BuddyServiceException;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.TimeUtil;

@Controller
@ExposesResourceFor(BuddyResource.class)
@RequestMapping(value = "/users/{userId}/buddies", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BuddyController extends ControllerBase
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
	 * @param userId The ID of the user. This is part of the URL.
	 * @return the list of buddies for the current user
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<BuddyResource>> getAllBuddies(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(
					createAllBuddiesCollectionResource(curieProvider, userId, buddyService.getBuddiesOfUser(userId)),
					HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/{buddyId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<BuddyResource> getBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID buddyId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{

			return createOkResponse(buddyService.getBuddy(buddyId), createResourceAssembler(userId));
		}
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<BuddyResource> addBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody PostPutBuddyDto postPutBuddy)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return createResponse(buddyService.addBuddyToRequestingUser(userId, convertToBuddy(postPutBuddy), this::getInviteUrl),
					HttpStatus.CREATED, createResourceAssembler(userId));
		}
	}

	@RequestMapping(value = "/{buddyId}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void removeBuddy(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID buddyId, @RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			buddyService.removeBuddy(userId, buddyId, Optional.ofNullable(messageStr));
		}
	}

	@RequestMapping(value = "/{buddyId}/goals/{goalId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalDto> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID buddyId, @PathVariable UUID goalId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return createOkResponse(getGoal(userId, buddyId, goalId), new GoalResourceAssembler(userId));
		}
	}

	@RequestMapping(value = "/{buddyId}/goals/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<GoalDto>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID buddyId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(createAllGoalsCollectionResource(userId, buddyId, getGoals(buddyId)), HttpStatus.OK);
		}
	}

	private GoalDto getGoal(UUID userId, UUID buddyId, UUID goalId)
	{
		BuddyDto buddy = buddyService.getBuddy(buddyId);
		Optional<GoalDto> goal = buddy.getGoals().stream().filter(g -> g.getGoalId().equals(goalId)).findAny();
		return goal.orElseThrow(() -> GoalServiceException.goalNotFoundByIdForBuddy(userId, buddyId, goalId));
	}

	private Set<GoalDto> getGoals(UUID buddyId)
	{
		return buddyService.getBuddy(buddyId).getGoals();
	}

	private BuddyDto convertToBuddy(PostPutBuddyDto postPutBuddy)
	{
		String userRelName = curieProvider.getNamespacedRelFor(BuddyDto.USER_REL_NAME);
		UserDto user = postPutBuddy.userInMap.get(userRelName);
		if (user == null)
		{
			throw BuddyServiceException.missingUser(userRelName);
		}
		return new BuddyDto(user, postPutBuddy.message, postPutBuddy.sendingStatus, postPutBuddy.receivingStatus,
				TimeUtil.utcNow());
	}

	public static Resources<BuddyResource> createAllBuddiesCollectionResource(CurieProvider curieProvider, UUID userId,
			Set<BuddyDto> allBuddiesOfUser)
	{
		return new Resources<>(new BuddyResourceAssembler(curieProvider, userId).toResources(allBuddiesOfUser),
				getAllBuddiesLinkBuilder(userId).withSelfRel());
	}

	static ControllerLinkBuilder getAllBuddiesLinkBuilder(UUID userId)
	{
		BuddyController methodOn = methodOn(BuddyController.class);
		return linkTo(methodOn.getAllBuddies(null, userId));
	}

	public String getInviteUrl(UUID newUserId, String tempPassword)
	{
		return UserController.getUserSelfLinkWithTempPassword(newUserId, tempPassword).getHref();
	}

	private BuddyResourceAssembler createResourceAssembler(UUID userId)
	{
		return new BuddyResourceAssembler(curieProvider, userId);
	}

	public static Resources<GoalDto> createAllGoalsCollectionResource(UUID userId, UUID buddyId, Set<GoalDto> allGoalsOfUser)
	{
		return new Resources<>(new GoalResourceAssembler(true, goalId -> getGoalLinkBuilder(userId, buddyId, goalId))
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

	static class PostPutBuddyDto
	{
		private final Map<String, UserDto> userInMap;
		private final String message;
		private final Status sendingStatus;
		private final Status receivingStatus;

		@JsonCreator
		public PostPutBuddyDto(@JsonProperty(value = "_embedded", required = true) Map<String, UserDto> userInMap,
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

	static class BuddyResource extends Resource<BuddyDto>
	{
		private final CurieProvider curieProvider;
		private final UUID userId;

		public BuddyResource(CurieProvider curieProvider, UUID userId, BuddyDto buddy)
		{
			super(buddy);
			this.curieProvider = curieProvider;
			this.userId = userId;
		}

		@JsonProperty("_embedded")
		public Map<String, Object> getEmbeddedResources()
		{
			if (getContent().getUser() == null)
			{
				return Collections.emptyMap();
			}

			HashMap<String, Object> result = new HashMap<>();
			result.put(curieProvider.getNamespacedRelFor(BuddyDto.USER_REL_NAME),
					new UserController.UserResourceAssembler(curieProvider).toResource(getContent().getUser()));
			if (getContent().getUser() != null && getContent().getGoals() != null)
			{
				result.put(curieProvider.getNamespacedRelFor(BuddyDto.GOALS_REL_NAME),
						BuddyController.createAllGoalsCollectionResource(userId, getContent().getId(), getContent().getGoals()));
			}
			return result;
		}
	}

	static class BuddyResourceAssembler extends ResourceAssemblerSupport<BuddyDto, BuddyResource>
	{
		private final UUID userId;
		private final CurieProvider curieProvider;

		public BuddyResourceAssembler(CurieProvider curieProvider, UUID userId)
		{
			super(BuddyController.class, BuddyResource.class);
			this.curieProvider = curieProvider;
			this.userId = userId;
		}

		@Override
		public BuddyResource toResource(BuddyDto buddy)
		{
			BuddyResource buddyResource = instantiateResource(buddy);
			ControllerLinkBuilder selfLinkBuilder = getSelfLinkBuilder(buddy.getId());
			addSelfLink(selfLinkBuilder, buddyResource);
			addEditLink(selfLinkBuilder, buddyResource);
			addUserPhotoLink(buddyResource);
			if (buddy.getSendingStatus() == Status.ACCEPTED)
			{
				addDayActivityOverviewsLink(buddyResource);
				addWeekActivityOverviewsLink(buddyResource);
			}
			return buddyResource;
		}

		@Override
		protected BuddyResource instantiateResource(BuddyDto buddy)
		{
			return new BuddyResource(curieProvider, userId, buddy);
		}

		private ControllerLinkBuilder getSelfLinkBuilder(UUID buddyId)
		{
			return getBuddyLinkBuilder(userId, buddyId);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, BuddyResource buddyResource)
		{
			buddyResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}

		private void addUserPhotoLink(BuddyResource buddyResource)
		{
			buddyResource.getContent().getUserPhotoId().ifPresent(userPhotoId -> buddyResource
					.add(linkTo(methodOn(UserPhotoController.class).getUserPhoto(userPhotoId)).withRel("userPhoto")));
		}

		private void addWeekActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(
					BuddyActivityController.getBuddyWeekActivityOverviewsLinkBuilder(userId, buddyResource.getContent().getId())
							.withRel(BuddyActivityController.WEEK_OVERVIEW_LINK));
		}

		private void addDayActivityOverviewsLink(BuddyResource buddyResource)
		{
			buddyResource.add(
					BuddyActivityController.getBuddyDayActivityOverviewsLinkBuilder(userId, buddyResource.getContent().getId())
							.withRel(BuddyActivityController.DAY_OVERVIEW_LINK));
		}
	}
}
