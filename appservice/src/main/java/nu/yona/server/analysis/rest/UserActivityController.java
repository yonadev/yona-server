/*******************************************************************************
 * Copyright (c) 2016, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.Lists;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.service.ActivityCommentMessageDto;
import nu.yona.server.analysis.service.ActivityDto;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.analysis.service.DayActivityOverviewDto;
import nu.yona.server.analysis.service.DayActivityWithBuddiesDto;
import nu.yona.server.analysis.service.DayActivityWithBuddiesDto.ActivityForOneUser;
import nu.yona.server.analysis.service.WeekActivityDto;
import nu.yona.server.analysis.service.WeekActivityOverviewDto;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.service.UserDeviceDto;
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
	@GetMapping(value = WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<PagedModel<WeekActivityOverviewResource>> getUserWeekActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PageableDefault(size = WEEKS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<WeekActivityOverviewDto> pagedResourcesAssembler)
	{
		return getWeekActivityOverviews(password, userId, pagedResourcesAssembler,
				() -> activityService.getUserWeekActivityOverviews(userId, pageable), new UserActivityLinkProvider(userId));
	}

	@GetMapping(value = WEEK_ACTIVITY_OVERVIEW_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<WeekActivityOverviewResource> getUserWeekActivityOverview(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr)
	{
		return getWeekActivityOverview(password, userId, dateStr,
				date -> activityService.getUserWeekActivityOverview(userId, date), new UserActivityLinkProvider(userId));
	}

	@GetMapping(value = DAY_OVERVIEWS_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<PagedModel<DayActivityOverviewResource>> getUserDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityDto>> pagedResourcesAssembler)
	{
		return getDayActivityOverviews(password, userId, pagedResourcesAssembler,
				() -> activityService.getUserDayActivityOverviews(userId, pageable), new UserActivityLinkProvider(userId));
	}

	@GetMapping(value = DAY_OVERVIEW_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<DayActivityOverviewResource> getUserDayActivityOverview(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr)
	{
		return getDayActivityOverview(password, userId, dateStr, date -> activityService.getUserDayActivityOverview(userId, date),
				new UserActivityLinkProvider(userId));
	}

	@GetMapping(value = WEEK_ACTIVITY_DETAIL_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<WeekActivityResource> getUserWeekActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId)
	{
		return getWeekActivityDetail(password, userId, dateStr,
				date -> activityService.getUserWeekActivityDetail(userId, date, goalId), new UserActivityLinkProvider(userId));
	}

	@GetMapping(value = WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<PagedModel<MessageDto>> getUserWeekActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(password, userId, pagedResourcesAssembler,
				() -> activityService.getUserWeekActivityDetailMessages(userId, WeekActivityDto.parseDate(dateStr), goalId,
						pageable));
	}

	@GetMapping(value = DAY_ACTIVITY_DETAIL_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<DayActivityResource> getUserDayActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId)
	{
		return getDayActivityDetail(password, userId, dateStr,
				date -> activityService.getUserDayActivityDetail(userId, date, goalId), new UserActivityLinkProvider(userId));
	}

	@GetMapping(value = DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT)
	@ResponseBody
	public HttpEntity<PagedModel<MessageDto>> getUserDayActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(password, userId, pagedResourcesAssembler,
				() -> activityService.getUserDayActivityDetailMessages(userId, DayActivityDto.parseDate(dateStr), goalId,
						pageable));
	}

	@GetMapping(value = DAY_ACTIVITY_DETAIL_URI_FRAGMENT + "/raw/")
	@ResponseBody
	public HttpEntity<ActivitiesResource> getRawActivities(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalId)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			Map<UUID, String> deviceAnonymizedIdToDeviceName = buildDeviceAnonymizedIdToDeviceNameMap(userId);
			return createOkResponse(activityService.getRawActivities(userId, DayActivityDto.parseDate(dateStr), goalId),
					createRawActivitiesResourceAssembler(userId, deviceAnonymizedIdToDeviceName, dateStr, goalId));
		}
	}

	private Map<UUID, String> buildDeviceAnonymizedIdToDeviceNameMap(UUID userId)
	{
		return userService.getUser(userId).getOwnPrivateData().getOwnDevices().stream()
				.collect(Collectors.toMap(UserDeviceDto::getDeviceAnonymizedId, UserDeviceDto::getName));
	}

	/**
	 * Get network and app activity of the buddies of this user for the current day, pageable to yesterday and before. The user's
	 * own activities are included if any buddy shares the same goal. Note that the name is slightly confusing, as the name
	 * suggests that the user activities are supplemented with the buddy activities, while in reality, the buddy activities are
	 * supplemented with the user activities. The name is retained because it reflects the external API (URL).
	 */
	@GetMapping(value = "/withBuddies/days/")
	@ResponseBody
	public HttpEntity<PagedModel<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@RequestParam(value = UserController.REQUESTING_DEVICE_ID_PARAM) UUID requestingDeviceId,
			@PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityWithBuddiesDto>> pagedResourcesAssembler)
	{
		return getDayActivityOverviewsWithBuddies(password, userId, requestingDeviceId, pagedResourcesAssembler,
				() -> activityService.getUserDayActivityOverviewsWithBuddies(userId, pageable));
	}

	private HttpEntity<PagedModel<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(
			Optional<String> password, UUID userId, UUID requestingDeviceId,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityWithBuddiesDto>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDto<DayActivityWithBuddiesDto>>> activitySupplier)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return getDayActivityOverviewsWithBuddies(userId, requestingDeviceId, pagedResourcesAssembler, activitySupplier);
		}
	}

	private ResponseEntity<PagedModel<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(UUID userId,
			UUID requestingDeviceId,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityWithBuddiesDto>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDto<DayActivityWithBuddiesDto>>> activitySupplier)
	{
		GoalIdMapping goalIdMapping = GoalIdMapping.createInstance(userAnonymizedService, userService.getUserEntityById(userId));

		return createOkResponse(activitySupplier.get(), pagedResourcesAssembler,
				createResourceAssembler(userId, requestingDeviceId, goalIdMapping));
	}

	private DayActivityOverviewWithBuddiesResourceAssembler createResourceAssembler(UUID userId, UUID requestingDeviceId,
			GoalIdMapping goalIdMapping)
	{
		return new DayActivityOverviewWithBuddiesResourceAssembler(userId, requestingDeviceId, goalIdMapping);
	}

	private ActivitiesResourceAssembler createRawActivitiesResourceAssembler(UUID userId,
			Map<UUID, String> deviceAnonymizedIdToDeviceName, String dateStr, UUID goalId)
	{
		return new ActivitiesResourceAssembler(userId, deviceAnonymizedIdToDeviceName, dateStr, goalId);
	}

	/**
	 * Get network and app activity of the buddies of this user for the given day. The user's own activities are included if any
	 * buddy shares the same goal. Note that the name is slightly confusing, as the name suggests that the user activities are
	 * supplemented with the buddy activities, while in reality, the buddy activities are supplemented with the user activities.
	 * The name is retained because it reflects the external API (URL).
	 */
	@GetMapping(value = "/withBuddies/days/{date}")
	@ResponseBody
	public HttpEntity<DayActivityOverviewWithBuddiesResource> getDayActivityOverviewWithBuddies(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId,
			@RequestParam(value = UserController.REQUESTING_DEVICE_ID_PARAM) UUID requestingDeviceId,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr)
	{
		return getDayActivityOverviewWithBuddies(password, userId, requestingDeviceId, dateStr,
				date -> activityService.getUserDayActivityOverviewWithBuddies(userId, date));
	}

	private HttpEntity<DayActivityOverviewWithBuddiesResource> getDayActivityOverviewWithBuddies(Optional<String> password,
			UUID userId, UUID requestingDeviceId, String dateStr,
			Function<LocalDate, DayActivityOverviewDto<DayActivityWithBuddiesDto>> activitySupplier)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return getDayActivityOverviewWithBuddies(userId, requestingDeviceId, dateStr, activitySupplier);
		}
	}

	private ResponseEntity<DayActivityOverviewWithBuddiesResource> getDayActivityOverviewWithBuddies(UUID userId,
			UUID requestingDeviceId, String dateStr,
			Function<LocalDate, DayActivityOverviewDto<DayActivityWithBuddiesDto>> activitySupplier)
	{
		LocalDate date = DayActivityDto.parseDate(dateStr);
		GoalIdMapping goalIdMapping = GoalIdMapping.createInstance(userAnonymizedService, userService.getUserEntityById(userId));
		return createOkResponse(activitySupplier.apply(date), createResourceAssembler(userId, requestingDeviceId, goalIdMapping));
	}

	@Override
	public void addLinks(GoalIdMapping goalIdMapping, IntervalActivity activity, ActivityCommentMessageDto message)
	{
		LinkProvider linkProvider = new UserActivityLinkProvider(goalIdMapping.getUserId());
		addStandardLinks(goalIdMapping, linkProvider, activity, message);
	}

	public static WebMvcLinkBuilder getUserDayActivityOverviewsLinkBuilder(UUID userId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityOverviews(Optional.empty(), userId, null, null));
	}

	public static WebMvcLinkBuilder getDayActivityOverviewsWithBuddiesLinkBuilder(UUID userId, UUID requestingDeviceId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(
				methodOn.getDayActivityOverviewsWithBuddies(Optional.empty(), userId, requestingDeviceId, (Pageable) null, null));
	}

	public static WebMvcLinkBuilder getUserWeekActivityOverviewsLinkBuilder(UUID userId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserWeekActivityOverviews(Optional.empty(), userId, null, null));
	}

	public static WebMvcLinkBuilder getUserWeekActivityOverviewLinkBuilder(UUID userId, String dateStr)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserWeekActivityOverview(Optional.empty(), userId, dateStr));
	}

	public static WebMvcLinkBuilder getUserDayActivityOverviewLinkBuilder(UUID userId, String dateStr)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityOverview(Optional.empty(), userId, dateStr));
	}

	public static WebMvcLinkBuilder getDayActivityOverviewWithBuddiesLinkBuilder(UUID userId, UUID requestingDeviceId,
			String dateStr)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getDayActivityOverviewWithBuddies(Optional.empty(), userId, requestingDeviceId, dateStr));
	}

	public static WebMvcLinkBuilder getUserDayActivityDetailLinkBuilder(UUID userId, String dateStr, UUID goalId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityDetail(Optional.empty(), userId, dateStr, goalId));
	}

	static WebMvcLinkBuilder getRawActivitiesLinkBuilder(UUID userId, String dateStr, UUID goalId)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getRawActivities(Optional.empty(), userId, dateStr, goalId));
	}

	static final class UserActivityLinkProvider implements LinkProvider
	{
		private final UUID userId;

		public UserActivityLinkProvider(UUID userId)
		{
			this.userId = userId;
		}

		@Override
		public WebMvcLinkBuilder getWeekActivityOverviewLinkBuilder(String dateStr)
		{
			return UserActivityController.getUserWeekActivityOverviewLinkBuilder(userId, dateStr);
		}

		@Override
		public WebMvcLinkBuilder getDayActivityOverviewLinkBuilder(String dateStr)
		{
			return UserActivityController.getUserDayActivityOverviewLinkBuilder(userId, dateStr);
		}

		@Override
		public WebMvcLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalId)
		{
			return UserActivityController.getUserDayActivityDetailLinkBuilder(userId, dateStr, goalId);
		}

		@Override
		public WebMvcLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalId)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserWeekActivityDetail(Optional.empty(), userId, dateStr, goalId));
		}

		@Override
		public WebMvcLinkBuilder getGoalLinkBuilder(UUID goalId)
		{
			return GoalController.getGoalLinkBuilder(userId, userId, goalId);
		}

		@Override
		public WebMvcLinkBuilder getDayActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserDayActivityDetailMessages(Optional.empty(), userId, dateStr, goalId, null, null));
		}

		@Override
		public Optional<WebMvcLinkBuilder> getDayActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId)
		{
			return Optional.empty();
		}

		@Override
		public WebMvcLinkBuilder getWeekActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserWeekActivityDetailMessages(Optional.empty(), userId, dateStr, goalId, null, null));
		}

		@Override
		public Optional<WebMvcLinkBuilder> getWeekActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId)
		{
			return Optional.empty();
		}

		@Override
		public Optional<WebMvcLinkBuilder> getBuddyLinkBuilder()
		{
			return Optional.empty();
		}
	}

	static class DayActivityOverviewWithBuddiesResource extends EntityModel<DayActivityOverviewDto<DayActivityWithBuddiesDto>>
	{
		private final UUID requestingUserId;
		private final UUID requestingDeviceId;
		private final GoalIdMapping goalIdMapping;

		public DayActivityOverviewWithBuddiesResource(UUID requestingUserId, UUID requestingDeviceId, GoalIdMapping goalIdMapping,
				DayActivityOverviewDto<DayActivityWithBuddiesDto> dayActivityOverview)
		{
			super(dayActivityOverview);
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
			this.goalIdMapping = goalIdMapping;
		}

		@Override
		@Nonnull
		public DayActivityOverviewDto<DayActivityWithBuddiesDto> getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		public List<DayActivityWithBuddiesResource> getDayActivities()
		{
			CollectionModel<DayActivityWithBuddiesResource> collectionModel = new DayActivityWithBuddiesResourceAssembler(
					requestingUserId, requestingDeviceId, goalIdMapping, getContent().getDateStr()).toCollectionModel(
					getContent().getDayActivities());
			return Lists.newArrayList(collectionModel);
		}
	}

	static class DayActivityOverviewWithBuddiesResourceAssembler extends
			RepresentationModelAssemblerSupport<DayActivityOverviewDto<DayActivityWithBuddiesDto>, DayActivityOverviewWithBuddiesResource>
	{
		private final UUID userId;
		private final UUID requestingDeviceId;
		private final GoalIdMapping goalIdMapping;

		public DayActivityOverviewWithBuddiesResourceAssembler(UUID userId, UUID requestingDeviceId, GoalIdMapping goalIdMapping)
		{
			super(ActivityControllerBase.class, DayActivityOverviewWithBuddiesResource.class);
			this.userId = userId;
			this.requestingDeviceId = requestingDeviceId;
			this.goalIdMapping = goalIdMapping;
		}

		@Override
		public @Nonnull DayActivityOverviewWithBuddiesResource toModel(
				@Nonnull DayActivityOverviewDto<DayActivityWithBuddiesDto> dayActivityOverview)
		{
			DayActivityOverviewWithBuddiesResource resource = instantiateModel(dayActivityOverview);
			addSelfLink(resource);
			return resource;
		}

		@Override
		protected @Nonnull DayActivityOverviewWithBuddiesResource instantiateModel(
				@Nonnull DayActivityOverviewDto<DayActivityWithBuddiesDto> dayActivityOverview)
		{
			return new DayActivityOverviewWithBuddiesResource(userId, requestingDeviceId, goalIdMapping, dayActivityOverview);
		}

		private void addSelfLink(DayActivityOverviewWithBuddiesResource resource)
		{
			resource.add(UserActivityController.getDayActivityOverviewWithBuddiesLinkBuilder(userId, requestingDeviceId,
					resource.getContent().getDateStr()).withSelfRel());
		}
	}

	static class DayActivityWithBuddiesResource extends EntityModel<DayActivityWithBuddiesDto>
	{
		private final UUID requestingUserId;
		private final UUID requestingDeviceId;
		private final GoalIdMapping goalIdMapping;
		private final String dateStr;

		public DayActivityWithBuddiesResource(UUID requestingUserId, UUID requestingDeviceId, GoalIdMapping goalIdMapping,
				String dateStr, @Nonnull DayActivityWithBuddiesDto dayActivity)
		{
			super(dayActivity);
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
			this.goalIdMapping = goalIdMapping;
			this.dateStr = dateStr;
		}

		@Override
		@Nonnull
		public DayActivityWithBuddiesDto getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		public List<ActivityForOneUserResource> getDayActivitiesForUsers()
		{
			CollectionModel<ActivityForOneUserResource> collectionModel = new ActivityForOneUserResourceAssembler(
					requestingUserId, requestingDeviceId, goalIdMapping, dateStr).toCollectionModel(
					getContent().getDayActivitiesForUsers());
			return Lists.newArrayList(collectionModel);
		}
	}

	static class DayActivityWithBuddiesResourceAssembler
			extends RepresentationModelAssemblerSupport<DayActivityWithBuddiesDto, DayActivityWithBuddiesResource>
	{
		private final UUID requestingUserId;
		private final UUID requestingDeviceId;
		private final GoalIdMapping goalIdMapping;
		private final String dateStr;

		public DayActivityWithBuddiesResourceAssembler(UUID requestingUserId, UUID requestingDeviceId,
				GoalIdMapping goalIdMapping, String dateStr)
		{
			super(ActivityControllerBase.class, DayActivityWithBuddiesResource.class);
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
			this.goalIdMapping = goalIdMapping;
			this.dateStr = dateStr;
		}

		@Override
		public @Nonnull DayActivityWithBuddiesResource toModel(@Nonnull DayActivityWithBuddiesDto dayActivity)
		{
			DayActivityWithBuddiesResource dayActivityResource = instantiateModel(dayActivity);
			addActivityCategoryLink(dayActivityResource);
			return dayActivityResource;
		}

		@Override
		protected @Nonnull DayActivityWithBuddiesResource instantiateModel(@Nonnull DayActivityWithBuddiesDto dayActivity)
		{
			return new DayActivityWithBuddiesResource(requestingUserId, requestingDeviceId, goalIdMapping, dateStr, dayActivity);
		}

		private void addActivityCategoryLink(@Nonnull DayActivityWithBuddiesResource dayActivityResource)
		{
			dayActivityResource.add(ActivityCategoryController.getActivityCategoryLinkBuilder(
					dayActivityResource.getContent().getActivityCategoryId()).withRel("activityCategory"));
		}
	}

	static class ActivityForOneUserResource extends EntityModel<ActivityForOneUser>
	{
		public ActivityForOneUserResource(ActivityForOneUser dayActivity)
		{
			super(dayActivity);
		}
	}

	static class ActivityForOneUserResourceAssembler
			extends RepresentationModelAssemblerSupport<ActivityForOneUser, ActivityForOneUserResource>
	{
		public static final LinkRelation USER_REL = LinkRelation.of("user");
		private final GoalIdMapping goalIdMapping;
		private final String dateStr;
		private final UUID requestingUserId;
		private final UUID requestingDeviceId;

		public ActivityForOneUserResourceAssembler(UUID requestingUserId, UUID requestingDeviceId, GoalIdMapping goalIdMapping,
				String dateStr)
		{
			super(ActivityControllerBase.class, ActivityForOneUserResource.class);
			this.requestingUserId = requestingUserId;
			this.requestingDeviceId = requestingDeviceId;
			this.goalIdMapping = goalIdMapping;
			this.dateStr = dateStr;
		}

		@Override
		public @Nonnull ActivityForOneUserResource toModel(@Nonnull ActivityForOneUser dayActivity)
		{
			ActivityForOneUserResource dayActivityResource = instantiateModel(dayActivity);

			UUID goalId = dayActivity.getGoalId();
			if (goalIdMapping.isUserGoal(goalId))
			{
				addGoalLinkForUser(requestingUserId, goalId, dayActivityResource);
				addDayDetailsLinkForUser(requestingUserId, goalId, dayActivityResource);
				addUserLink(requestingUserId, requestingDeviceId, dayActivityResource);
			}
			else
			{
				UUID buddyId = goalIdMapping.getBuddyId(goalId);
				UUID userId = goalIdMapping.getBuddyUserId(buddyId);
				addGoalLinkForBuddy(requestingUserId, userId, goalId, dayActivityResource);
				addDayDetailsLinkForBuddy(requestingUserId, buddyId, goalId, dayActivityResource);
				addBuddyLink(requestingUserId, buddyId, dayActivityResource);
			}
			return dayActivityResource;
		}

		@Override
		protected @Nonnull ActivityForOneUserResource instantiateModel(@Nonnull ActivityForOneUser dayActivity)
		{
			return new ActivityForOneUserResource(dayActivity);
		}

		private void addGoalLinkForUser(UUID userId, UUID goalId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(GoalController.getGoalLinkBuilder(requestingUserId, userId, goalId).withRel("goal"));
		}

		private void addDayDetailsLinkForUser(UUID userId, UUID goalId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(
					UserActivityController.getUserDayActivityDetailLinkBuilder(userId, dateStr, goalId).withRel("dayDetails"));
		}

		private void addUserLink(UUID userId, UUID requestingDeviceId, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(UserController.getUserLink(USER_REL, userId, Optional.of(requestingDeviceId)));
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

	@JsonRootName("activity")
	private static class ActivityWithDeviceDto
	{
		private final ActivityDto dto;
		private final String deviceName;

		private ActivityWithDeviceDto(ActivityDto dto, String deviceName)
		{
			this.dto = dto;
			this.deviceName = deviceName;
		}

		@JsonUnwrapped
		public ActivityDto getDto()
		{
			return dto;
		}

		public int getDurationMinutes()
		{
			return (int) ChronoUnit.MINUTES.between(dto.getStartTime(), dto.getEndTime());
		}

		@SuppressWarnings("unused") // Used as JSon property
		public Duration getDuration()
		{
			return Duration.ofMinutes(getDurationMinutes());
		}

		@SuppressWarnings("unused") // Used as JSon property
		public String getDeviceName()
		{
			return deviceName;
		}
	}

	public static class ActivitiesResource extends CollectionModel<ActivityWithDeviceDto>
	{
		public ActivitiesResource(Map<UUID, String> deviceAnonymizedIdToDeviceName, List<ActivityDto> rawActivities)
		{
			super(wrapEnrichActivitiesWithDeviceName(deviceAnonymizedIdToDeviceName, rawActivities));
		}

		private static Iterable<ActivityWithDeviceDto> wrapEnrichActivitiesWithDeviceName(
				Map<UUID, String> deviceAnonymizedIdToDeviceName, List<ActivityDto> rawActivities)
		{
			return rawActivities.stream()
					.map(dto -> new ActivityWithDeviceDto(dto, getDeviceName(deviceAnonymizedIdToDeviceName, dto))).toList();
		}

		private static String getDeviceName(Map<UUID, String> deviceAnonymizedIdToDeviceName, ActivityDto dto)
		{
			return dto.getDeviceAnonymizedId().map(deviceAnonymizedIdToDeviceName::get).orElse("n/a");
		}
	}

	public static class ActivitiesResourceAssembler
			extends RepresentationModelAssemblerSupport<List<ActivityDto>, ActivitiesResource>
	{
		private final UUID userId;
		private final String dateStr;
		private final UUID goalId;
		private final Map<UUID, String> deviceAnonymizedIdToDeviceName;

		public ActivitiesResourceAssembler(UUID userId, Map<UUID, String> deviceAnonymizedIdToDeviceName, String dateStr,
				UUID goalId)
		{
			super(UserActivityController.class, ActivitiesResource.class);
			this.userId = userId;
			this.deviceAnonymizedIdToDeviceName = deviceAnonymizedIdToDeviceName;
			this.dateStr = dateStr;
			this.goalId = goalId;
		}

		@Override
		public @Nonnull ActivitiesResource toModel(@Nonnull List<ActivityDto> rawActivities)
		{
			ActivitiesResource resource = instantiateModel(rawActivities);
			addSelfLink(resource);
			return resource;
		}

		@Override
		protected @Nonnull ActivitiesResource instantiateModel(@Nonnull List<ActivityDto> rawActivities)
		{
			return new ActivitiesResource(deviceAnonymizedIdToDeviceName, rawActivities);
		}

		private void addSelfLink(ActivitiesResource resource)
		{
			resource.add(getRawActivitiesLinkBuilder(userId, dateStr, goalId).withSelfRel());
		}
	}
}
