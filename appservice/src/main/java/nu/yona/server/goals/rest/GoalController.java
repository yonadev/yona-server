/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.rest;

import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import jakarta.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.goals.service.GoalServiceException;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.RestUtil;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(GoalDto.class)
@RequestMapping(value = "/users/{userId}/goals", produces = { MediaType.APPLICATION_JSON_VALUE })
public class GoalController extends ControllerBase
{
	private static final LinkRelation ACTIVITY_CATEGORY_REL = LinkRelation.of("activityCategory");

	@Autowired
	private UserService userService;

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private GoalService goalService;

	@Autowired
	private CurieProvider curieProvider;

	@GetMapping(value = "/")
	@ResponseBody
	public HttpEntity<CollectionModel<GoalDto>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = UserController.REQUESTING_USER_ID_PARAM) String requestingUserIdStr, @PathVariable UUID userId)
	{
		UUID requestingUserId = RestUtil.parseUuid(requestingUserIdStr);
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(requestingUserId)))
		{
			Set<GoalDto> goals = (requestingUserId.equals(userId)) ?
					goalService.getGoalsOfUser(userId) :
					getGoalsOfBuddyUser(requestingUserId, userId);
			return new ResponseEntity<>(createAllGoalsCollectionResource(requestingUserId, userId, goals), HttpStatus.OK);
		}
	}

	private Set<GoalDto> getGoalsOfBuddyUser(UUID requestingUserId, UUID userId)
	{
		return buddyService.getUserOfBuddy(requestingUserId, userId).getPrivateData().getGoalsIncludingHistoryItems()
				.orElseThrow(() -> new IllegalStateException("Goals of user " + userId + " are not available"));
	}

	@GetMapping(value = "/{goalId}")
	@ResponseBody
	public HttpEntity<GoalDto> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = UserController.REQUESTING_USER_ID_PARAM) String requestingUserIdStr, @PathVariable UUID userId,
			@PathVariable UUID goalId)
	{
		UUID requestingUserId = RestUtil.parseUuid(requestingUserIdStr);
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(requestingUserId)))
		{
			GoalDto goal = (requestingUserId.equals(userId)) ?
					goalService.getGoalForUserId(userId, goalId) :
					getGoalOfBuddyUser(requestingUserId, userId, goalId);
			return createOkResponse(goal, createResourceAssembler(requestingUserId, userId));
		}
	}

	private GoalDto getGoalOfBuddyUser(UUID requestingUserId, UUID userId, UUID goalId)
	{
		return getGoalsOfBuddyUser(requestingUserId, userId).stream().filter(g -> g.getGoalId().equals(goalId)).findFirst()
				.orElseThrow(() -> GoalServiceException.goalNotFoundByIdForUser(userId, goalId));
	}

	@PostMapping(value = "/")
	@ResponseBody
	public HttpEntity<GoalDto> addGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody GoalDto goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			setActivityCategoryId(goal);
			return createResponse(goalService.addGoal(userId, goal, Optional.ofNullable(messageStr)), HttpStatus.CREATED,
					createResourceAssembler(userId));
		}
	}

	@PutMapping(value = "/{goalId}")
	@ResponseBody
	public HttpEntity<GoalDto> updateGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable UUID goalId, @RequestBody GoalDto goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			setActivityCategoryId(goal);
			return createOkResponse(goalService.updateGoal(userId, goalId, goal, Optional.ofNullable(messageStr)),
					createResourceAssembler(userId));
		}
	}

	@DeleteMapping(value = "/{goalId}")
	@ResponseBody
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable UUID goalId, @RequestParam(value = "message", required = false) String messageStr)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			goalService.deleteGoalAndInformBuddies(userId, goalId, Optional.ofNullable(messageStr));
		}
	}

	private GoalResourceAssembler createResourceAssembler(UUID userId)
	{
		return createResourceAssembler(userId, userId);
	}

	private GoalResourceAssembler createResourceAssembler(UUID requestingUserId, UUID userId)
	{
		return new GoalResourceAssembler(requestingUserId, userId);
	}

	public static CollectionModel<GoalDto> createAllGoalsCollectionResource(UUID requestingUserId, UUID userId,
			Set<GoalDto> allGoalsOfUser)
	{
		return CollectionModel.of(new GoalResourceAssembler(requestingUserId, userId).toCollectionModel(allGoalsOfUser),
				getAllGoalsLinkBuilder(requestingUserId, userId).withSelfRel());
	}

	public static WebMvcLinkBuilder getAllGoalsLinkBuilder(UUID requestingUserId, UUID userId)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getAllGoals(Optional.empty(), requestingUserId.toString(), userId));
	}

	private void setActivityCategoryId(GoalDto goal)
	{
		Link activityCategoryLink = goal.getLink(curieProvider.getNamespacedRelFor(ACTIVITY_CATEGORY_REL))
				.orElseThrow(InvalidDataException::missingActivityCategoryLink);
		UUID activityCategoryId = determineActivityCategoryId(activityCategoryLink.getHref());
		goal.setActivityCategoryId(activityCategoryId);
	}

	private static UUID determineActivityCategoryId(String activityCategoryUrl)
	{
		return RestUtil.parseUuid(activityCategoryUrl.substring(activityCategoryUrl.lastIndexOf('/') + 1));
	}

	public static WebMvcLinkBuilder getGoalLinkBuilder(UUID requestingUserId, UUID userId, UUID goalId)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getGoal(Optional.empty(), requestingUserId.toString(), userId, goalId));
	}

	public static class GoalResourceAssembler extends RepresentationModelAssemblerSupport<GoalDto, GoalDto>
	{
		private final boolean canBeEditable;
		private final Function<UUID, WebMvcLinkBuilder> selfLinkBuilderSupplier;

		public GoalResourceAssembler(UUID requestingUserId, UUID userId)
		{
			this(true, goalId -> getGoalLinkBuilder(requestingUserId, userId, goalId));
		}

		public GoalResourceAssembler(boolean canBeEditable, Function<UUID, WebMvcLinkBuilder> selfLinkBuilderSupplier)
		{
			super(GoalController.class, GoalDto.class);
			this.canBeEditable = canBeEditable;
			this.selfLinkBuilderSupplier = selfLinkBuilderSupplier;
		}

		@Override
		public @Nonnull GoalDto toModel(@Nonnull GoalDto goal)
		{
			goal.removeLinks();
			WebMvcLinkBuilder selfLinkBuilder = selfLinkBuilderSupplier.apply(goal.getGoalId());
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
		protected @Nonnull GoalDto instantiateModel(@Nonnull GoalDto goal)
		{
			return goal;
		}

		private void addSelfLink(WebMvcLinkBuilder selfLinkBuilder, GoalDto goalResource)
		{
			goalResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(WebMvcLinkBuilder selfLinkBuilder, GoalDto goalResource)
		{
			goalResource.add(selfLinkBuilder.withRel(IanaLinkRelations.EDIT));
		}
	}
}
