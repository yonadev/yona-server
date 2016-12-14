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

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(GoalDTO.class)
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
	public HttpEntity<Resources<GoalDTO>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userId),
				() -> new ResponseEntity<Resources<GoalDTO>>(
						createAllGoalsCollectionResource(userId, goalService.getGoalsOfUser(userId)), HttpStatus.OK));
	}

	@RequestMapping(value = "/{goalId}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalDTO> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID goalId)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userId),
				() -> createResponse(userId, goalService.getGoalForUserId(userId, goalId), HttpStatus.OK));
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<GoalDTO> addGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody GoalDTO goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		setActivityCategoryId(goal);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userId), () -> createResponse(userId,
				goalService.addGoal(userId, goal, Optional.ofNullable(messageStr)), HttpStatus.CREATED));
	}

	@RequestMapping(value = "/{goalId}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<GoalDTO> updateGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID goalId, @RequestBody GoalDTO goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		setActivityCategoryId(goal);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userId), () -> createResponse(userId,
				goalService.updateGoal(userId, goalId, goal, Optional.ofNullable(messageStr)), HttpStatus.OK));
	}

	@RequestMapping(value = "/{goalId}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void removeGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID goalId, @RequestParam(value = "message", required = false) String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userId), () -> {
			goalService.removeGoal(userId, goalId, Optional.ofNullable(messageStr));
			return null;
		});
	}

	public static Resources<GoalDTO> createAllGoalsCollectionResource(UUID userId, Set<GoalDTO> allGoalsOfUser)
	{
		return new Resources<>(new GoalResourceAssembler(userId).toResources(allGoalsOfUser),
				getAllGoalsLinkBuilder(userId).withSelfRel());
	}

	private static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID userId)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getAllGoals(null, userId));
	}

	private HttpEntity<GoalDTO> createResponse(UUID userId, GoalDTO goal, HttpStatus status)
	{
		return new ResponseEntity<GoalDTO>(new GoalResourceAssembler(userId).toResource(goal), status);
	}

	private void setActivityCategoryId(GoalDTO goal)
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

	public static class GoalResourceAssembler extends ResourceAssemblerSupport<GoalDTO, GoalDTO>
	{
		private final boolean canBeEditable;
		private final Function<UUID, ControllerLinkBuilder> selfLinkBuilderSupplier;

		public GoalResourceAssembler(UUID userId)
		{
			this(true, (goalId) -> getGoalLinkBuilder(userId, goalId));
		}

		public GoalResourceAssembler(boolean canBeEditable, Function<UUID, ControllerLinkBuilder> selfLinkBuilderSupplier)
		{
			super(GoalController.class, GoalDTO.class);
			this.canBeEditable = canBeEditable;
			this.selfLinkBuilderSupplier = selfLinkBuilderSupplier;
		}

		@Override
		public GoalDTO toResource(GoalDTO goal)
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

		private void addActivityCategoryLink(GoalDTO goalResource)
		{
			goalResource.add(ActivityCategoryController.getActivityCategoryLinkBuilder(goalResource.getActivityCategoryId())
					.withRel(GoalController.ACTIVITY_CATEGORY_REL));
		}

		@Override
		protected GoalDTO instantiateResource(GoalDTO goal)
		{
			return goal;
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, GoalDTO goalResource)
		{
			goalResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, GoalDTO goalResource)
		{
			goalResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}
	}
}
