/*******************************************************************************
 * Copyright (c) 2016, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.common.collect.Lists;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.service.ActivityCommentMessageDto;
import nu.yona.server.analysis.service.ActivityService;
import nu.yona.server.analysis.service.DayActivityDto;
import nu.yona.server.analysis.service.DayActivityOverviewDto;
import nu.yona.server.analysis.service.WeekActivityDto;
import nu.yona.server.analysis.service.WeekActivityOverviewDto;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.service.GoalIdMapping;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Activity controller base class.
 */
public abstract class ActivityControllerBase extends ControllerBase
{
	public static final LinkRelation DAY_DETAIL_REL = LinkRelation.of("dayDetails");
	public static final LinkRelation WEEK_DETAIL_REL = LinkRelation.of("weekDetails");
	public static final LinkRelation DAY_OVERVIEW_REL = LinkRelation.of("dailyActivityReports");
	public static final LinkRelation WEEK_OVERVIEW_REL = LinkRelation.of("weeklyActivityReports");

	@Autowired
	protected ActivityService activityService;

	@Autowired
	protected UserService userService;

	@Autowired
	protected UserAnonymizedService userAnonymizedService;

	@Autowired
	private MessageController messageController;

	protected static final String WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT = "/weeks/";
	protected static final String WEEK_ACTIVITY_OVERVIEW_URI_FRAGMENT = "/weeks/{date}";
	protected static final String DAY_OVERVIEWS_URI_FRAGMENT = "/days/";
	protected static final String DAY_OVERVIEW_URI_FRAGMENT = "/days/{date}";
	protected static final String WEEK_ACTIVITY_DETAIL_URI_FRAGMENT = "/weeks/{date}/details/{goalId}";
	protected static final String WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT = WEEK_ACTIVITY_DETAIL_URI_FRAGMENT + "/messages/";
	protected static final String DAY_ACTIVITY_DETAIL_URI_FRAGMENT = "/days/{date}/details/{goalId}";
	protected static final String DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT = DAY_ACTIVITY_DETAIL_URI_FRAGMENT + "/messages/";
	protected static final String GOAL_PATH_VARIABLE = "goalId";
	protected static final String DATE_PATH_VARIABLE = "date";
	protected static final int WEEKS_DEFAULT_PAGE_SIZE = 2;
	protected static final int DAYS_DEFAULT_PAGE_SIZE = 3;
	protected static final int MESSAGES_DEFAULT_PAGE_SIZE = 4;

	protected HttpEntity<PagedModel<WeekActivityOverviewResource>> getWeekActivityOverviews(Optional<String> password,
			UUID userId, PagedResourcesAssembler<WeekActivityOverviewDto> pagedResourcesAssembler,
			Supplier<Page<WeekActivityOverviewDto>> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createOkResponse(activitySupplier.get(), pagedResourcesAssembler,
					createWeekActivityOverviewResourceAssembler(linkProvider));
		}
	}

	protected HttpEntity<WeekActivityOverviewResource> getWeekActivityOverview(Optional<String> password, UUID userId,
			String dateStr, Function<LocalDate, WeekActivityOverviewDto> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			LocalDate date = WeekActivityDto.parseDate(dateStr);
			return createOkResponse(activitySupplier.apply(date), createWeekActivityOverviewResourceAssembler(linkProvider));
		}
	}

	protected HttpEntity<PagedModel<DayActivityOverviewResource>> getDayActivityOverviews(Optional<String> password, UUID userId,
			PagedResourcesAssembler<DayActivityOverviewDto<DayActivityDto>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDto<DayActivityDto>>> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createOkResponse(activitySupplier.get(), pagedResourcesAssembler,
					createDayActivityOverviewResourceAssembler(linkProvider));
		}
	}

	protected HttpEntity<DayActivityOverviewResource> getDayActivityOverview(Optional<String> password, UUID userId,
			String dateStr, Function<LocalDate, DayActivityOverviewDto<DayActivityDto>> activitySupplier,
			LinkProvider linkProvider)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			LocalDate date = DayActivityDto.parseDate(dateStr);
			return createOkResponse(activitySupplier.apply(date), createDayActivityOverviewResourceAssembler(linkProvider));
		}
	}

	protected HttpEntity<WeekActivityResource> getWeekActivityDetail(Optional<String> password, UUID userId, String dateStr,
			Function<LocalDate, WeekActivityDto> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			LocalDate date = WeekActivityDto.parseDate(dateStr);
			return createOkResponse(activitySupplier.apply(date), createWeekActivityResourceAssembler(linkProvider));
		}
	}

	protected HttpEntity<DayActivityResource> getDayActivityDetail(Optional<String> password, UUID userId, String dateStr,
			Function<LocalDate, DayActivityDto> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			LocalDate date = DayActivityDto.parseDate(dateStr);
			return createOkResponse(activitySupplier.apply(date), createDayActivityResourceAssembler(linkProvider));
		}
	}

	protected HttpEntity<PagedModel<MessageDto>> getActivityDetailMessages(Optional<String> password, UUID userId,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler, Supplier<Page<MessageDto>> messageSupplier)
	{
		try (CryptoSession ignored = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			User user = userService.getValidatedUserEntity(userId);
			return messageController.createOkResponse(user, messageSupplier.get(), pagedResourcesAssembler);
		}
	}

	private WeekActivityOverviewResourceAssembler createWeekActivityOverviewResourceAssembler(LinkProvider linkProvider)
	{
		return new WeekActivityOverviewResourceAssembler(linkProvider);
	}

	private DayActivityOverviewResourceAssembler createDayActivityOverviewResourceAssembler(LinkProvider linkProvider)
	{
		return new DayActivityOverviewResourceAssembler(linkProvider);
	}

	private WeekActivityResourceAssembler createWeekActivityResourceAssembler(LinkProvider linkProvider)
	{
		return new WeekActivityResourceAssembler(linkProvider, true);
	}

	private DayActivityResourceAssembler createDayActivityResourceAssembler(LinkProvider linkProvider)
	{
		return new DayActivityResourceAssembler(linkProvider, true, true);
	}

	public abstract void addLinks(GoalIdMapping goalIdMapping, IntervalActivity activity, ActivityCommentMessageDto message);

	protected void addStandardLinks(GoalIdMapping goalIdMapping, LinkProvider linkProvider, IntervalActivity activity,
			ActivityCommentMessageDto message)
	{
		if (activity instanceof WeekActivity)
		{
			addWeekDetailsLink(linkProvider, activity, message);
		}
		else
		{
			addDayDetailsLink(linkProvider, activity, message);
		}
		addThreadHeadMessageLink(goalIdMapping.getUserId(), message);
		message.getRepliedMessageId().ifPresent(rmid -> addRepliedMessageLink(goalIdMapping.getUserId(), message, rmid));
	}

	private void addWeekDetailsLink(LinkProvider linkProvider, IntervalActivity activity, ActivityCommentMessageDto message)
	{
		message.add(linkProvider.getWeekActivityDetailLinkBuilder(WeekActivityDto.formatDate(activity.getStartDate()),
				activity.getGoal().getId()).withRel(WEEK_DETAIL_REL));
	}

	private void addDayDetailsLink(LinkProvider linkProvider, IntervalActivity activity, ActivityCommentMessageDto message)
	{
		message.add(linkProvider.getDayActivityDetailLinkBuilder(DayActivityDto.formatDate(activity.getStartDate()),
				activity.getGoal().getId()).withRel(DAY_DETAIL_REL));
	}

	private void addThreadHeadMessageLink(UUID userId, ActivityCommentMessageDto message)
	{
		message.add(
				MessageController.getAnonymousMessageLinkBuilder(userId, message.getThreadHeadMessageId()).withRel("threadHead"));
	}

	private void addRepliedMessageLink(UUID userId, ActivityCommentMessageDto message, long repliedMessageId)
	{
		message.add(MessageController.getAnonymousMessageLinkBuilder(userId, repliedMessageId).withRel("repliedMessage"));
	}

	interface LinkProvider
	{
		WebMvcLinkBuilder getWeekActivityOverviewLinkBuilder(String dateStr);

		WebMvcLinkBuilder getDayActivityOverviewLinkBuilder(String dateStr);

		WebMvcLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalId);

		WebMvcLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalId);

		WebMvcLinkBuilder getGoalLinkBuilder(UUID goalId);

		WebMvcLinkBuilder getDayActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId);

		Optional<WebMvcLinkBuilder> getDayActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId);

		WebMvcLinkBuilder getWeekActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId);

		Optional<WebMvcLinkBuilder> getWeekActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId);

		Optional<WebMvcLinkBuilder> getBuddyLinkBuilder();
	}

	static class WeekActivityResource extends EntityModel<WeekActivityDto>
	{
		private final LinkProvider linkProvider;

		public WeekActivityResource(LinkProvider linkProvider, @Nonnull WeekActivityDto weekActivity)
		{
			super(weekActivity);
			this.linkProvider = linkProvider;
		}

		@Override
		@Nonnull
		public WeekActivityDto getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		@JsonFormat(shape = JsonFormat.Shape.OBJECT)
		public Map<DayOfWeek, DayActivityResource> getDayActivities()
		{
			DayActivityResourceAssembler a = new DayActivityResourceAssembler(linkProvider, false, false);
			return getContent().getDayActivities().entrySet().stream()
					.collect(Collectors.toMap(Map.Entry::getKey, e -> a.toModel(e.getValue())));
		}
	}

	static class WeekActivityOverviewResource extends EntityModel<WeekActivityOverviewDto>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResource(LinkProvider linkProvider, @Nonnull WeekActivityOverviewDto weekActivityOverview)
		{
			super(weekActivityOverview);
			this.linkProvider = linkProvider;
		}

		@Override
		@Nonnull
		public WeekActivityOverviewDto getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		public List<WeekActivityResource> getWeekActivities()
		{
			CollectionModel<WeekActivityResource> collectionModel = new WeekActivityResourceAssembler(linkProvider,
					false).toCollectionModel(getContent().getWeekActivities());
			return Lists.newArrayList(collectionModel);
		}
	}

	static class DayActivityResource extends EntityModel<DayActivityDto>
	{
		public DayActivityResource(DayActivityDto dayActivity)
		{
			super(dayActivity);
		}

		@Override
		@Nonnull
		public DayActivityDto getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

	}

	static class DayActivityOverviewResource extends EntityModel<DayActivityOverviewDto<DayActivityDto>>
	{
		private final LinkProvider linkProvider;

		public DayActivityOverviewResource(LinkProvider linkProvider,
				@Nonnull DayActivityOverviewDto<DayActivityDto> dayActivityOverview)
		{
			super(dayActivityOverview);
			this.linkProvider = linkProvider;
		}

		@Override
		@Nonnull
		public DayActivityOverviewDto<DayActivityDto> getContent()
		{
			return Objects.requireNonNull(super.getContent());
		}

		public List<DayActivityResource> getDayActivities()
		{
			CollectionModel<DayActivityResource> collectionModel = new DayActivityResourceAssembler(linkProvider, true,
					false).toCollectionModel(getContent().getDayActivities());
			return Lists.newArrayList(collectionModel);
		}
	}

	static class WeekActivityOverviewResourceAssembler
			extends RepresentationModelAssemblerSupport<WeekActivityOverviewDto, WeekActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, WeekActivityOverviewResource.class);
			this.linkProvider = linkProvider;
		}

		@Override
		public @Nonnull WeekActivityOverviewResource toModel(@Nonnull WeekActivityOverviewDto weekActivityOverview)
		{
			WeekActivityOverviewResource resource = instantiateModel(weekActivityOverview);
			addSelfLink(resource);
			return resource;
		}

		@Override
		protected @Nonnull WeekActivityOverviewResource instantiateModel(@Nonnull WeekActivityOverviewDto weekActivityOverview)
		{
			return new WeekActivityOverviewResource(linkProvider, weekActivityOverview);
		}

		private void addSelfLink(@Nonnull WeekActivityOverviewResource resource)
		{
			resource.add(linkProvider.getWeekActivityOverviewLinkBuilder(resource.getContent().getDateStr()).withSelfRel());
		}
	}

	static class WeekActivityResourceAssembler extends RepresentationModelAssemblerSupport<WeekActivityDto, WeekActivityResource>
	{
		private final LinkProvider linkProvider;
		private final boolean isWeekDetail;

		public WeekActivityResourceAssembler(LinkProvider linkProvider, boolean isWeekDetail)
		{
			super(ActivityControllerBase.class, WeekActivityResource.class);
			this.linkProvider = linkProvider;
			this.isWeekDetail = isWeekDetail;
		}

		@Override
		public @Nonnull WeekActivityResource toModel(@Nonnull WeekActivityDto weekActivity)
		{
			WeekActivityResource weekActivityResource = instantiateModel(weekActivity);
			addWeekDetailsLink(weekActivityResource, (isWeekDetail) ? IanaLinkRelations.SELF : WEEK_DETAIL_REL);
			addGoalLink(weekActivityResource);
			if (isWeekDetail)
			{
				addMessagesLink(weekActivityResource);
				addAddCommentLink(weekActivityResource);
				addPrevNextLinks(weekActivityResource);
				addBuddyLink(weekActivityResource);
			}
			return weekActivityResource;
		}

		@Override
		protected @Nonnull WeekActivityResource instantiateModel(@Nonnull WeekActivityDto weekActivity)
		{
			return new WeekActivityResource(linkProvider, weekActivity);
		}

		private void addWeekDetailsLink(@Nonnull WeekActivityResource weekActivityResource, LinkRelation rel)
		{
			weekActivityResource.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getDateStr(),
					weekActivityResource.getContent().getGoalId()).withRel(rel));
		}

		private void addGoalLink(@Nonnull WeekActivityResource weekActivityResource)
		{
			weekActivityResource.add(
					linkProvider.getGoalLinkBuilder(weekActivityResource.getContent().getGoalId()).withRel("goal"));
		}

		private void addMessagesLink(@Nonnull WeekActivityResource weekActivityResource)
		{
			weekActivityResource.add(
					linkProvider.getWeekActivityDetailMessagesLinkBuilder(weekActivityResource.getContent().getDateStr(),
							weekActivityResource.getContent().getGoalId()).withRel("messages"));
		}

		private void addAddCommentLink(@Nonnull WeekActivityResource weekActivityResource)
		{
			Optional<WebMvcLinkBuilder> linkBuilder = linkProvider.getWeekActivityDetailAddCommentLinkBuilder(
					weekActivityResource.getContent().getDateStr(), weekActivityResource.getContent().getGoalId());
			linkBuilder.ifPresent(lb -> weekActivityResource.add(lb.withRel("addComment")));
		}

		private void addPrevNextLinks(@Nonnull WeekActivityResource weekActivityResource)
		{
			if (weekActivityResource.getContent().hasPrevious())
			{
				weekActivityResource.add(
						linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getPreviousDateStr(),
								weekActivityResource.getContent().getGoalId()).withRel(IanaLinkRelations.PREV));
			}
			if (weekActivityResource.getContent().hasNext())
			{
				weekActivityResource.add(
						linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getNextDateStr(),
								weekActivityResource.getContent().getGoalId()).withRel(IanaLinkRelations.NEXT));
			}
		}

		private void addBuddyLink(WeekActivityResource weekActivityResource)
		{
			Optional<WebMvcLinkBuilder> linkBuilder = linkProvider.getBuddyLinkBuilder();
			linkBuilder.ifPresent(lb -> weekActivityResource.add(lb.withRel(BuddyController.BUDDY_LINK)));
		}
	}

	static class DayActivityResourceAssembler extends RepresentationModelAssemblerSupport<DayActivityDto, DayActivityResource>
	{
		private final LinkProvider linkProvider;
		private final boolean addGoalLink;
		private final boolean isDayDetail;

		public DayActivityResourceAssembler(LinkProvider linkProvider, boolean addGoalLink, boolean isDayDetail)
		{
			super(ActivityControllerBase.class, DayActivityResource.class);
			this.linkProvider = linkProvider;
			this.addGoalLink = addGoalLink;
			this.isDayDetail = isDayDetail;
		}

		@Override
		public @Nonnull DayActivityResource toModel(@Nonnull DayActivityDto dayActivity)
		{
			DayActivityResource dayActivityResource = instantiateModel(dayActivity);
			addDayDetailsLink(dayActivityResource, (isDayDetail) ? IanaLinkRelations.SELF : DAY_DETAIL_REL);
			if (addGoalLink)
			{
				addGoalLink(dayActivityResource);
			}
			if (isDayDetail)
			{
				addMessagesLink(dayActivityResource);
				addAddCommentLink(dayActivityResource);
				addPrevNextLinks(dayActivityResource);
				addBuddyLink(dayActivityResource);
			}
			return dayActivityResource;
		}

		@Override
		protected @Nonnull DayActivityResource instantiateModel(@Nonnull DayActivityDto dayActivity)
		{
			return new DayActivityResource(dayActivity);
		}

		private void addGoalLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource.add(
					linkProvider.getGoalLinkBuilder(dayActivityResource.getContent().getGoalId()).withRel("goal"));
		}

		private void addDayDetailsLink(DayActivityResource dayActivityResource, LinkRelation rel)
		{
			dayActivityResource.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getDateStr(),
					dayActivityResource.getContent().getGoalId()).withRel(rel));
		}

		private void addMessagesLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource.add(
					linkProvider.getDayActivityDetailMessagesLinkBuilder(dayActivityResource.getContent().getDateStr(),
							dayActivityResource.getContent().getGoalId()).withRel("messages"));
		}

		private void addAddCommentLink(DayActivityResource dayActivityResource)
		{
			Optional<WebMvcLinkBuilder> linkBuilder = linkProvider.getDayActivityDetailAddCommentLinkBuilder(
					dayActivityResource.getContent().getDateStr(), dayActivityResource.getContent().getGoalId());
			linkBuilder.ifPresent(lb -> dayActivityResource.add(lb.withRel("addComment")));
		}

		private void addPrevNextLinks(DayActivityResource dayActivityResource)
		{
			if (dayActivityResource.getContent().hasPrevious())
			{
				dayActivityResource.add(
						linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getPreviousDateStr(),
								dayActivityResource.getContent().getGoalId()).withRel(IanaLinkRelations.PREV));
			}
			if (dayActivityResource.getContent().hasNext())
			{
				dayActivityResource.add(
						linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getNextDateStr(),
								dayActivityResource.getContent().getGoalId()).withRel(IanaLinkRelations.NEXT));
			}
		}

		private void addBuddyLink(DayActivityResource dayActivityResource)
		{
			Optional<WebMvcLinkBuilder> linkBuilder = linkProvider.getBuddyLinkBuilder();
			linkBuilder.ifPresent(lb -> dayActivityResource.add(lb.withRel(BuddyController.BUDDY_LINK)));
		}
	}

	static class DayActivityOverviewResourceAssembler
			extends RepresentationModelAssemblerSupport<DayActivityOverviewDto<DayActivityDto>, DayActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public DayActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, DayActivityOverviewResource.class);
			this.linkProvider = linkProvider;
		}

		@Override
		public @Nonnull DayActivityOverviewResource toModel(@Nonnull DayActivityOverviewDto<DayActivityDto> dayActivityOverview)
		{
			DayActivityOverviewResource resource = instantiateModel(dayActivityOverview);
			addSelfLink(resource);
			return resource;
		}

		@Override
		protected @Nonnull DayActivityOverviewResource instantiateModel(
				@Nonnull DayActivityOverviewDto<DayActivityDto> dayActivityOverview)
		{
			return new DayActivityOverviewResource(linkProvider, dayActivityOverview);
		}

		private void addSelfLink(DayActivityOverviewResource resource)
		{
			resource.add(linkProvider.getDayActivityOverviewLinkBuilder(resource.getContent().getDateStr()).withSelfRel());
		}
	}
}
