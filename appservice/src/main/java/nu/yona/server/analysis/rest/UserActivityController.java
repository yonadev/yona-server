/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.service.ActivityCommentMessageDto;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.analysis.service.DayActivityOverviewDto;
import nu.yona.server.analysis.service.DayActivityWithBuddiesDto;
import nu.yona.server.analysis.service.DayActivityWithBuddiesDto.ActivityForOneUser;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.analysis.service.WeekActivityDto;
import nu.yona.server.analysis.service.WeekActivityOverviewDto;
import nu.yona.server.goals.rest.ActivityCategoryController;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.service.GoalIdMapping;

/*
 * Controller to retrieve activity data for a user.
 */
@Controller
@RequestMapping(value = "/users/{userId}/activity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserActivityController extends ActivityControllerBase
{
	@RequestMapping(value = WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<WeekActivityOverviewResource>> getUserWeekActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PageableDefault(size = WEEKS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<WeekActivityOverviewDto> pagedResourcesAssembler)
	{
		return getWeekActivityOverviews(password, userId, pagedResourcesAssembler, () -> activityService.getUserWeekActivityOverviews(userId, pageable),
				new UserActivityLinkProvider(userId));
	}

	@RequestMapping(value = DAY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewResource>> getUserDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityDto>> pagedResourcesAssembler)
	{
		return getDayActivityOverviews(password, userId, pagedResourcesAssembler, () -> activityService.getUserDayActivityOverviews(userId, pageable),
				new UserActivityLinkProvider(userId));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<WeekActivityResource> getUserWeekActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId)
	{
		return getWeekActivityDetail(password, userId, dateStr,
				date -> activityService.getUserWeekActivityDetail(userId, date, goalId), new UserActivityLinkProvider(userId));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageDto>> getUserWeekActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(
				password, userId, pagedResourcesAssembler, () -> activityService
						.getUserWeekActivityDetailMessages(userId, WeekActivityDto.parseDate(dateStr), goalId, pageable), new UserActivityLinkProvider(userId));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<DayActivityResource> getUserDayActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId)
	{
		return getDayActivityDetail(password, userId, dateStr,
				date -> activityService.getUserDayActivityDetail(userId, date, goalId), new UserActivityLinkProvider(userId));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageDto>> getUserDayActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(
				password, userId, pagedResourcesAssembler, () -> activityService
						.getUserDayActivityDetailMessages(userId, DayActivityDto.parseDate(dateStr), goalId, pageable), new UserActivityLinkProvider(userId));
	}

	@RequestMapping(value = "/withBuddies/days/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityWithBuddiesDto>> pagedResourcesAssembler)
	{
		return getDayActivityOverviewsWithBuddies(password, userId, pagedResourcesAssembler, () -> activityService.getUserDayActivityOverviewsWithBuddies(userId, pageable),
				new UserActivityLinkProvider(userId));
	}

	private HttpEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(
			Optional<String> password, UUID userId, PagedResourcesAssembler<DayActivityOverviewDto<DayActivityWithBuddiesDto>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDto<DayActivityWithBuddiesDto>>> activitySupplier,
			LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return getDayActivityOverviewsWithBuddies(userId, pagedResourcesAssembler, activitySupplier);
		}
	}

	private ResponseEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(UUID userId,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityWithBuddiesDto>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDto<DayActivityWithBuddiesDto>>> activitySupplier)
	{
		GoalIdMapping goalIdMapping = GoalIdMapping.createInstance(userService.getPrivateUser(userId));
		return new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
				new DayActivityOverviewWithBuddiesResourceAssembler(goalIdMapping)), HttpStatus.OK);
	}

	@Override
	public void addLinks(GoalIdMapping goalIdMapping, IntervalActivity activity, ActivityCommentMessageDto message)
	{
		LinkProvider linkProvider = new UserActivityLinkProvider(goalIdMapping.getUserId());
		addStandardLinks(goalIdMapping, linkProvider, activity, message);
	}

	public static ControllerLinkBuilder getUserDayActivityOverviewsLinkBuilder(UUID userId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityOverviews(null, userId, null, null));
	}

	public static ControllerLinkBuilder getDayActivityOverviewsWithBuddiesLinkBuilder(UUID userId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getDayActivityOverviewsWithBuddies(null, userId, null, null));
	}

	public static ControllerLinkBuilder getUserWeekActivityOverviewsLinkBuilder(UUID userId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserWeekActivityOverviews(null, userId, null, null));
	}

	public static ControllerLinkBuilder getUserDayActivityDetailLinkBuilder(UUID userId, String dateStr, UUID goalId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityDetail(null, userId, dateStr, goalId));
	}

	static final class UserActivityLinkProvider implements LinkProvider
	{
		private final UUID userId;

		public UserActivityLinkProvider(UUID userId)
		{
			this.userId = userId;
		}

		@Override
		public ControllerLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalId)
		{
			return UserActivityController.getUserDayActivityDetailLinkBuilder(userId, dateStr, goalId);
		}

		@Override
		public ControllerLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalId)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserWeekActivityDetail(null, userId, dateStr, goalId));
		}

		@Override
		public ControllerLinkBuilder getGoalLinkBuilder(UUID goalId)
		{
			return GoalController.getGoalLinkBuilder(userId, goalId);
		}

		@Override
		public ControllerLinkBuilder getDayActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserDayActivityDetailMessages(Optional.empty(), userId, dateStr, goalId, null, null));
		}

		@Override
		public Optional<ControllerLinkBuilder> getDayActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId)
		{
			return Optional.empty();
		}

		@Override
		public ControllerLinkBuilder getWeekActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserWeekActivityDetailMessages(Optional.empty(), userId, dateStr, goalId, null, null));
		}

		@Override
		public Optional<ControllerLinkBuilder> getWeekActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId)
		{
			return Optional.empty();
		}
	}

	static class DayActivityOverviewWithBuddiesResource extends Resource<DayActivityOverviewDto<DayActivityWithBuddiesDto>>
	{
		private final GoalIdMapping goalIdMapping;

		public DayActivityOverviewWithBuddiesResource(GoalIdMapping goalIdMapping,
				DayActivityOverviewDto<DayActivityWithBuddiesDto> dayActivityOverview)
		{
			super(dayActivityOverview);
			this.goalIdMapping = goalIdMapping;
		}

		public List<DayActivityWithBuddiesResource> getDayActivities()
		{
			return new DayActivityWithBuddiesResourceAssembler(goalIdMapping, getContent().getDateStr())
					.toResources(getContent().getDayActivities());
		}
	}

	static class DayActivityOverviewWithBuddiesResourceAssembler extends
			ResourceAssemblerSupport<DayActivityOverviewDto<DayActivityWithBuddiesDto>, DayActivityOverviewWithBuddiesResource>
	{
		private final GoalIdMapping goalIdMapping;

		public DayActivityOverviewWithBuddiesResourceAssembler(GoalIdMapping goalIdMapping)
		{
			super(ActivityControllerBase.class, DayActivityOverviewWithBuddiesResource.class);
			this.goalIdMapping = goalIdMapping;
		}

		@Override
		public DayActivityOverviewWithBuddiesResource toResource(
				DayActivityOverviewDto<DayActivityWithBuddiesDto> dayActivityOverview)
		{
			return instantiateResource(dayActivityOverview);
		}

		@Override
		protected DayActivityOverviewWithBuddiesResource instantiateResource(
				DayActivityOverviewDto<DayActivityWithBuddiesDto> dayActivityOverview)
		{
			return new DayActivityOverviewWithBuddiesResource(goalIdMapping, dayActivityOverview);
		}
	}

	static class DayActivityWithBuddiesResource extends Resource<DayActivityWithBuddiesDto>
	{
		private final GoalIdMapping goalIdMapping;
		private final String dateStr;

		public DayActivityWithBuddiesResource(GoalIdMapping goalIdMapping, String dateStr, DayActivityWithBuddiesDto dayActivity)
		{
			super(dayActivity);
			this.goalIdMapping = goalIdMapping;
			this.dateStr = dateStr;
		}

		public List<ActivityForOneUserResource> getDayActivitiesForUsers()
		{
			return new ActivityForOneUserResourceAssembler(goalIdMapping, dateStr)
					.toResources(getContent().getDayActivitiesForUsers());
		}
	}

	static class DayActivityWithBuddiesResourceAssembler
			extends ResourceAssemblerSupport<DayActivityWithBuddiesDto, DayActivityWithBuddiesResource>
	{
		private final GoalIdMapping goalIdMapping;
		private final String dateStr;

		public DayActivityWithBuddiesResourceAssembler(GoalIdMapping goalIdMapping, String dateStr)
		{
			super(ActivityControllerBase.class, DayActivityWithBuddiesResource.class);
			this.goalIdMapping = goalIdMapping;
			this.dateStr = dateStr;
		}

		@Override
		public DayActivityWithBuddiesResource toResource(DayActivityWithBuddiesDto dayActivity)
		{
			DayActivityWithBuddiesResource dayActivityResource = instantiateResource(dayActivity);
			addActivityCategoryLink(dayActivityResource);
			return dayActivityResource;
		}

		@Override
		protected DayActivityWithBuddiesResource instantiateResource(DayActivityWithBuddiesDto dayActivity)
		{
			return new DayActivityWithBuddiesResource(goalIdMapping, dateStr, dayActivity);
		}

		private void addActivityCategoryLink(DayActivityWithBuddiesResource dayActivityResource)
		{
			dayActivityResource.add(ActivityCategoryController
					.getActivityCategoryLinkBuilder(dayActivityResource.getContent().getActivityCategoryId())
					.withRel("activityCategory"));
		}
	}

	static class ActivityForOneUserResource extends Resource<ActivityForOneUser>
	{
		public ActivityForOneUserResource(ActivityForOneUser dayActivity)
		{
			super(dayActivity);
		}
	}

	static class ActivityForOneUserResourceAssembler
			extends ResourceAssemblerSupport<ActivityForOneUser, ActivityForOneUserResource>
	{
		private final GoalIdMapping goalIdMapping;
		private final String dateStr;

		public ActivityForOneUserResourceAssembler(GoalIdMapping goalIdMapping, String dateStr)
		{
			super(ActivityControllerBase.class, ActivityForOneUserResource.class);
			this.goalIdMapping = goalIdMapping;
			this.dateStr = dateStr;
		}

		@Override
		public ActivityForOneUserResource toResource(ActivityForOneUser dayActivity)
		{
			ActivityForOneUserResource dayActivityResource = instantiateResource(dayActivity);

			UUID goalId = dayActivity.getGoalId();
			UUID userId = goalIdMapping.getUserId();
			if (goalIdMapping.isUserGoal(goalId))
			{
				addGoalLinkForUser(userId, goalId, dayActivityResource);
				addDayDetailsLinkForUser(userId, goalId, dayActivityResource);
				addUserLink(userId, dayActivityResource);
			}
			else
			{
				UUID buddyId = goalIdMapping.getBuddyId(goalId);
				addGoalLinkForBuddy(userId, buddyId, goalId, dayActivityResource);
				addDayDetailsLinkForBuddy(userId, buddyId, goalId, dayActivityResource);
				addBuddyLink(userId, buddyId, dayActivityResource);
			}
			return dayActivityResource;
		}

		@Override
		protected ActivityForOneUserResource instantiateResource(ActivityForOneUser dayActivity)
		{
			return new ActivityForOneUserResource(dayActivity);
		}

		private void addGoalLinkForUser(UUID userId, UUID goalId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(GoalController.getGoalLinkBuilder(userId, goalId).withRel("goal"));
		}

		private void addDayDetailsLinkForUser(UUID userId, UUID goalId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(
					UserActivityController.getUserDayActivityDetailLinkBuilder(userId, dateStr, goalId).withRel("dayDetails"));
		}

		private void addUserLink(UUID userId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(UserController.getPrivateUserLink("user", userId));
		}

		private void addGoalLinkForBuddy(UUID userId, UUID buddyId, UUID goalId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(BuddyController.getGoalLinkBuilder(userId, buddyId, goalId).withRel("goal"));
		}

		private void addDayDetailsLinkForBuddy(UUID userId, UUID buddyId, UUID goalId,
				ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(BuddyActivityController.getBuddyDayActivityDetailLinkBuilder(userId, buddyId, dateStr, goalId)
					.withRel("dayDetails"));
		}

		private void addBuddyLink(UUID userId, UUID buddyId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(BuddyController.getBuddyLinkBuilder(userId, buddyId).withRel(BuddyController.BUDDY_LINK));
		}
	}
}
