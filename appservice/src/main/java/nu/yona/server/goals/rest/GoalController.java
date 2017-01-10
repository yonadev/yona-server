/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
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

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(GoalDto.class)
@RequestMapping(value = "/users/{userId}/goals", produces = { MediaType.APPLICATION_JSON_VALUE })
public class GoalController
{
	private static final String ACTIVITY_CATEGORY_REL = "activityCategory";

	@Autowired
	private UserService userService;

	@Autowired
	private GoalService goalService;

	@Autowired
	private CurieProvider curieProvider;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<GoalDto>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(createAllGoalsCollectionResource(userId, goalService.getGoalsOfUser(userId)),
					HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/{goalId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalDto> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID goalId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return createResponse(userId, goalService.getGoalForUserId(userId, goalId), HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<GoalDto> addGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody GoalDto goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			setActivityCategoryId(goal);
			return createResponse(userId, goalService.addGoal(userId, goal, Optional.ofNullable(messageStr)), HttpStatus.CREATED);
		}
	}

	@RequestMapping(value = "/{goalId}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<GoalDto> updateGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID goalId, @RequestBody GoalDto goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			setActivityCategoryId(goal);
			return createResponse(userId, goalService.updateGoal(userId, goalId, goal, Optional.ofNullable(messageStr)),
					HttpStatus.OK);
		}
	}

	@RequestMapping(value = "/{goalId}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void removeGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID goalId, @RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			goalService.removeGoal(userId, goalId, Optional.ofNullable(messageStr));
		}
	}

	public static Resources<GoalDto> createAllGoalsCollectionResource(UUID userId, Set<GoalDto> allGoalsOfUser)
	{
		return new Resources<>(new GoalResourceAssembler(userId).toResources(allGoalsOfUser),
				getAllGoalsLinkBuilder(userId).withSelfRel());
	}

	private static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID userId)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getAllGoals(null, userId));
	}

	private HttpEntity<GoalDto> createResponse(UUID userId, GoalDto goal, HttpStatus status)
	{
		return new ResponseEntity<>(new GoalResourceAssembler(userId).toResource(goal), status);
	}

	private void setActivityCategoryId(GoalDto goal)
	{
		Link activityCategoryLink = goal.getLink(curieProvider.getNamespacedRelFor(ACTIVITY_CATEGORY_REL));
		if (activityCategoryLink == null)
		{
			throw InvalidDataException.missingActivityCategoryLink();
		}
		UUID activityCategoryId = determineActivityCategoryId(activityCategoryLink.getHref());
		goal.setActivityCategoryId(activityCategoryId);
	}

	private static UUID determineActivityCategoryId(String activityCategoryUrl)
	{
		return UUID.fromString(activityCategoryUrl.substring(activityCategoryUrl.lastIndexOf('/') + 1));
	}

	public static ControllerLinkBuilder getGoalLinkBuilder(UUID userId, UUID goalId)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getGoal(Optional.empty(), userId, goalId));
	}

	public static class GoalResourceAssembler extends ResourceAssemblerSupport<GoalDto, GoalDto>
	{
		private final boolean canBeEditable;
		private final Function<UUID, ControllerLinkBuilder> selfLinkBuilderSupplier;

		public GoalResourceAssembler(UUID userId)
		{
			this(true, (goalId) -> getGoalLinkBuilder(userId, goalId));
		}

		public GoalResourceAssembler(boolean canBeEditable, Function<UUID, ControllerLinkBuilder> selfLinkBuilderSupplier)
		{
			super(GoalController.class, GoalDto.class);
			this.canBeEditable = canBeEditable;
			this.selfLinkBuilderSupplier = selfLinkBuilderSupplier;
		}

		@Override
		public GoalDto toResource(GoalDto goal)
		{
			goal.removeLinks();
			ControllerLinkBuilder selfLinkBuilder = selfLinkBuilderSupplier.apply(goal.getGoalId());
			addSelfLink(selfLinkBuilder, goal);
			if (canBeEditable && !goal.isMandatory())
			{
				addEditLink(selfLinkBuilder, goal);
			}
			addActivityCategoryLink(goal);
			return goal;
		}

		private void addActivityCategoryLink(GoalDto goalResource)
		{
			goalResource.add(ActivityCategoryController.getActivityCategoryLinkBuilder(goalResource.getActivityCategoryId())
					.withRel(GoalController.ACTIVITY_CATEGORY_REL));
		}

		@Override
		protected GoalDto instantiateResource(GoalDto goal)
		{
			return goal;
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, GoalDto goalResource)
		{
			goalResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, GoalDto goalResource)
		{
			goalResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}
	}
}
