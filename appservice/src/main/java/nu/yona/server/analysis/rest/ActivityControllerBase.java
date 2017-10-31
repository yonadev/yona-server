/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonFormat;

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
import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.GoalIdMapping;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Activity controller base class.
 */
abstract class ActivityControllerBase
{
	public static final String DAY_DETAIL_LINK = "dayDetails";
	public static final String WEEK_DETAIL_LINK = "weekDetails";
	public static final String DAY_OVERVIEW_LINK = "dailyActivityReports";
	public static final String WEEK_OVERVIEW_LINK = "weeklyActivityReports";

	@Autowired
	protected ActivityService activityService;

	@Autowired
	protected UserService userService;

	@Autowired
	private CurieProvider curieProvider;

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
	protected static final String PREV_REL = "prev"; // IANA reserved, so will not be prefixed
	protected static final String NEXT_REL = "next"; // IANA reserved, so will not be prefixed

	protected HttpEntity<PagedResources<WeekActivityOverviewResource>> getWeekActivityOverviews(Optional<String> password,
			UUID userId, PagedResourcesAssembler<WeekActivityOverviewDto> pagedResourcesAssembler,
			Supplier<Page<WeekActivityOverviewDto>> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
					new WeekActivityOverviewResourceAssembler(linkProvider)), HttpStatus.OK);
		}
	}

	protected HttpEntity<WeekActivityOverviewResource> getWeekActivityOverview(Optional<String> password, UUID userId,
			String dateStr, Function<LocalDate, WeekActivityOverviewDto> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			LocalDate date = WeekActivityDto.parseDate(dateStr);
			return new ResponseEntity<>(
					new WeekActivityOverviewResourceAssembler(linkProvider).toResource(activitySupplier.apply(date)),
					HttpStatus.OK);
		}
	}

	protected HttpEntity<PagedResources<DayActivityOverviewResource>> getDayActivityOverviews(Optional<String> password,
			UUID userId, PagedResourcesAssembler<DayActivityOverviewDto<DayActivityDto>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDto<DayActivityDto>>> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
					new DayActivityOverviewResourceAssembler(linkProvider)), HttpStatus.OK);
		}
	}

	protected HttpEntity<DayActivityOverviewResource> getDayActivityOverview(Optional<String> password, UUID userId,
			String dateStr, Function<LocalDate, DayActivityOverviewDto<DayActivityDto>> activitySupplier,
			LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			LocalDate date = DayActivityDto.parseDate(dateStr);
			return new ResponseEntity<>(
					new DayActivityOverviewResourceAssembler(linkProvider).toResource(activitySupplier.apply(date)),
					HttpStatus.OK);
		}
	}

	protected HttpEntity<WeekActivityResource> getWeekActivityDetail(Optional<String> password, UUID userId, String dateStr,
			Function<LocalDate, WeekActivityDto> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			LocalDate date = WeekActivityDto.parseDate(dateStr);
			return new ResponseEntity<>(
					new WeekActivityResourceAssembler(linkProvider, true).toResource(activitySupplier.apply(date)),
					HttpStatus.OK);
		}
	}

	protected HttpEntity<DayActivityResource> getDayActivityDetail(Optional<String> password, UUID userId, String dateStr,
			Function<LocalDate, DayActivityDto> activitySupplier, LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			LocalDate date = DayActivityDto.parseDate(dateStr);
			return new ResponseEntity<>(
					new DayActivityResourceAssembler(linkProvider, true, true).toResource(activitySupplier.apply(date)),
					HttpStatus.OK);
		}
	}

	protected HttpEntity<PagedResources<MessageDto>> getActivityDetailMessages(Optional<String> password, UUID userId,
			PagedResourcesAssembler<MessageDto> pagedResourcesAssembler, Supplier<Page<MessageDto>> messageSupplier,
			LinkProvider linkProvider)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password, () -> userService.canAccessPrivateData(userId)))
		{
			return new ResponseEntity<>(
					pagedResourcesAssembler.toResource(messageSupplier.get(),
							new MessageResourceAssembler(curieProvider, createGoalIdMapping(userId), messageController)),
					HttpStatus.OK);
		}
	}

	protected GoalIdMapping createGoalIdMapping(UUID userId)
	{
		return GoalIdMapping.createInstance(userService.getPrivateUser(userId));
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
		if (!message.getSenderUser().get().getUserId().equals(goalIdMapping.getUserId()))
		{
			UUID buddyId = determineBuddyId(goalIdMapping, message);
			addBuddyLink(goalIdMapping.getUserId(), buddyId, message);
		}
		addThreadHeadMessageLink(goalIdMapping.getUserId(), message);
		message.getRepliedMessageId().ifPresent(rmid -> addRepliedMessageLink(goalIdMapping.getUserId(), message));
	}

	private UUID determineBuddyId(GoalIdMapping goalIdMapping, ActivityCommentMessageDto message)
	{
		return goalIdMapping.getUser().getPrivateData().getBuddies().stream()
				.filter(b -> b.getUser().getUserId().equals(message.getSenderUser().get().getUserId())).map(BuddyDto::getId).findAny()
				.orElseThrow(() -> new IllegalArgumentException(
						"User with ID " + message.getSenderUser().get().getUserId() + "is not a buddy"));
	}

	private void addWeekDetailsLink(LinkProvider linkProvider, IntervalActivity activity, ActivityCommentMessageDto message)
	{
		message.add(linkProvider
				.getWeekActivityDetailLinkBuilder(WeekActivityDto.formatDate(activity.getStartDate()), activity.getGoal().getId())
				.withRel(WEEK_DETAIL_LINK));
	}

	private void addDayDetailsLink(LinkProvider linkProvider, IntervalActivity activity, ActivityCommentMessageDto message)
	{
		message.add(linkProvider
				.getDayActivityDetailLinkBuilder(DayActivityDto.formatDate(activity.getStartDate()), activity.getGoal().getId())
				.withRel(DAY_DETAIL_LINK));
	}

	private void addThreadHeadMessageLink(UUID userId, ActivityCommentMessageDto message)
	{
		message.add(
				MessageController.getAnonymousMessageLinkBuilder(userId, message.getThreadHeadMessageId()).withRel("threadHead"));
	}

	private void addRepliedMessageLink(UUID userId, ActivityCommentMessageDto message)
	{
		message.add(MessageController.getAnonymousMessageLinkBuilder(userId, message.getRepliedMessageId().get())
				.withRel("repliedMessage"));
	}

	private void addBuddyLink(UUID userId, UUID buddyId, ActivityCommentMessageDto message)
	{
		message.add(BuddyController.getBuddyLinkBuilder(userId, buddyId).withRel(BuddyController.BUDDY_LINK));
	}

	interface LinkProvider
	{
		public ControllerLinkBuilder getWeekActivityOverviewLinkBuilder(String dateStr);

		public ControllerLinkBuilder getDayActivityOverviewLinkBuilder(String dateStr);

		public ControllerLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalId);

		public ControllerLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalId);

		public ControllerLinkBuilder getGoalLinkBuilder(UUID goalId);

		public ControllerLinkBuilder getDayActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId);

		public Optional<ControllerLinkBuilder> getDayActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId);

		public ControllerLinkBuilder getWeekActivityDetailMessagesLinkBuilder(String dateStr, UUID goalId);

		public Optional<ControllerLinkBuilder> getWeekActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalId);
	}

	static class WeekActivityResource extends Resource<WeekActivityDto>
	{
		private final LinkProvider linkProvider;

		public WeekActivityResource(LinkProvider linkProvider, WeekActivityDto weekActivity)
		{
			super(weekActivity);
			this.linkProvider = linkProvider;
		}

		@JsonFormat(shape = JsonFormat.Shape.OBJECT)
		public Map<DayOfWeek, DayActivityResource> getDayActivities()
		{
			DayActivityResourceAssembler a = new DayActivityResourceAssembler(linkProvider, false, false);
			return getContent().getDayActivities().entrySet().stream()
					.collect(Collectors.toMap(Map.Entry::getKey, e -> a.toResource(e.getValue())));
		}
	}

	static class WeekActivityOverviewResource extends Resource<WeekActivityOverviewDto>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResource(LinkProvider linkProvider, WeekActivityOverviewDto weekActivityOverview)
		{
			super(weekActivityOverview);
			this.linkProvider = linkProvider;
		}

		public List<WeekActivityResource> getWeekActivities()
		{
			return new WeekActivityResourceAssembler(linkProvider, false).toResources(getContent().getWeekActivities());
		}
	}

	static class DayActivityResource extends Resource<DayActivityDto>
	{
		public DayActivityResource(DayActivityDto dayActivity)
		{
			super(dayActivity);
		}
	}

	static class DayActivityOverviewResource extends Resource<DayActivityOverviewDto<DayActivityDto>>
	{
		private final LinkProvider linkProvider;

		public DayActivityOverviewResource(LinkProvider linkProvider, DayActivityOverviewDto<DayActivityDto> dayActivityOverview)
		{
			super(dayActivityOverview);
			this.linkProvider = linkProvider;
		}

		public List<DayActivityResource> getDayActivities()
		{
			return new DayActivityResourceAssembler(linkProvider, true, false).toResources(getContent().getDayActivities());
		}
	}

	static class WeekActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<WeekActivityOverviewDto, WeekActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, WeekActivityOverviewResource.class);
			this.linkProvider = linkProvider;
		}

		@Override
		public WeekActivityOverviewResource toResource(WeekActivityOverviewDto weekActivityOverview)
		{
			WeekActivityOverviewResource resource = instantiateResource(weekActivityOverview);
			addSelfLink(resource);
			return resource;
		}

		@Override
		protected WeekActivityOverviewResource instantiateResource(WeekActivityOverviewDto weekActivityOverview)
		{
			return new WeekActivityOverviewResource(linkProvider, weekActivityOverview);
		}

		private void addSelfLink(Resource<WeekActivityOverviewDto> resource)
		{
			resource.add(linkProvider.getWeekActivityOverviewLinkBuilder(resource.getContent().getDateStr()).withSelfRel());
		}
	}

	static class WeekActivityResourceAssembler extends ResourceAssemblerSupport<WeekActivityDto, WeekActivityResource>
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
		public WeekActivityResource toResource(WeekActivityDto weekActivity)
		{
			WeekActivityResource weekActivityResource = instantiateResource(weekActivity);
			addWeekDetailsLink(weekActivityResource, (isWeekDetail) ? Link.REL_SELF : "weekDetails");
			addGoalLink(weekActivityResource);
			if (isWeekDetail)
			{
				addMessagesLink(weekActivityResource);
				addAddCommentLink(weekActivityResource);
				addPrevNextLinks(weekActivityResource);
			}
			return weekActivityResource;
		}

		@Override
		protected WeekActivityResource instantiateResource(WeekActivityDto weekActivity)
		{
			return new WeekActivityResource(linkProvider, weekActivity);
		}

		private void addWeekDetailsLink(WeekActivityResource weekActivityResource, String rel)
		{
			weekActivityResource.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getDateStr(),
					weekActivityResource.getContent().getGoalId()).withRel(rel));
		}

		private void addGoalLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource
					.add(linkProvider.getGoalLinkBuilder(weekActivityResource.getContent().getGoalId()).withRel("goal"));
		}

		private void addMessagesLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource
					.add(linkProvider.getWeekActivityDetailMessagesLinkBuilder(weekActivityResource.getContent().getDateStr(),
							weekActivityResource.getContent().getGoalId()).withRel("messages"));
		}

		private void addAddCommentLink(WeekActivityResource weekActivityResource)
		{
			Optional<ControllerLinkBuilder> linkBuilder = linkProvider.getWeekActivityDetailAddCommentLinkBuilder(
					weekActivityResource.getContent().getDateStr(), weekActivityResource.getContent().getGoalId());
			linkBuilder.ifPresent(lb -> weekActivityResource.add(lb.withRel("addComment")));
		}

		private void addPrevNextLinks(WeekActivityResource weekActivityResource)
		{
			if (weekActivityResource.getContent().hasPrevious())
			{
				weekActivityResource
						.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getPreviousDateStr(),
								weekActivityResource.getContent().getGoalId()).withRel(PREV_REL));
			}
			if (weekActivityResource.getContent().hasNext())
			{
				weekActivityResource
						.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getNextDateStr(),
								weekActivityResource.getContent().getGoalId()).withRel(NEXT_REL));
			}
		}
	}

	static class DayActivityResourceAssembler extends ResourceAssemblerSupport<DayActivityDto, DayActivityResource>
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
		public DayActivityResource toResource(DayActivityDto dayActivity)
		{
			DayActivityResource dayActivityResource = instantiateResource(dayActivity);
			addDayDetailsLink(dayActivityResource, (isDayDetail) ? Link.REL_SELF : "dayDetails");
			if (addGoalLink)
			{
				addGoalLink(dayActivityResource);
			}
			if (isDayDetail)
			{
				addMessagesLink(dayActivityResource);
				addAddCommentLink(dayActivityResource);
				addPrevNextLinks(dayActivityResource);
			}
			return dayActivityResource;
		}

		@Override
		protected DayActivityResource instantiateResource(DayActivityDto dayActivity)
		{
			return new DayActivityResource(dayActivity);
		}

		private void addGoalLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(linkProvider.getGoalLinkBuilder(dayActivityResource.getContent().getGoalId()).withRel("goal"));
		}

		private void addDayDetailsLink(DayActivityResource dayActivityResource, String rel)
		{
			dayActivityResource.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getDateStr(),
					dayActivityResource.getContent().getGoalId()).withRel(rel));
		}

		private void addMessagesLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(linkProvider.getDayActivityDetailMessagesLinkBuilder(dayActivityResource.getContent().getDateStr(),
							dayActivityResource.getContent().getGoalId()).withRel("messages"));
		}

		private void addAddCommentLink(DayActivityResource dayActivityResource)
		{
			Optional<ControllerLinkBuilder> linkBuilder = linkProvider.getDayActivityDetailAddCommentLinkBuilder(
					dayActivityResource.getContent().getDateStr(), dayActivityResource.getContent().getGoalId());
			linkBuilder.ifPresent(lb -> dayActivityResource.add(lb.withRel("addComment")));
		}

		private void addPrevNextLinks(DayActivityResource dayActivityResource)
		{
			if (dayActivityResource.getContent().hasPrevious())
			{
				dayActivityResource
						.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getPreviousDateStr(),
								dayActivityResource.getContent().getGoalId()).withRel(PREV_REL));
			}
			if (dayActivityResource.getContent().hasNext())
			{
				dayActivityResource
						.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getNextDateStr(),
								dayActivityResource.getContent().getGoalId()).withRel(NEXT_REL));
			}
		}
	}

	static class DayActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<DayActivityOverviewDto<DayActivityDto>, DayActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public DayActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, DayActivityOverviewResource.class);
			this.linkProvider = linkProvider;
		}

		@Override
		public DayActivityOverviewResource toResource(DayActivityOverviewDto<DayActivityDto> dayActivityOverview)
		{
			DayActivityOverviewResource resource = instantiateResource(dayActivityOverview);
			addSelfLink(resource);
			return resource;
		}

		@Override
		protected DayActivityOverviewResource instantiateResource(DayActivityOverviewDto<DayActivityDto> dayActivityOverview)
		{
			return new DayActivityOverviewResource(linkProvider, dayActivityOverview);
		}

		private void addSelfLink(Resource<DayActivityOverviewDto<DayActivityDto>> resource)
		{
			resource.add(linkProvider.getDayActivityOverviewLinkBuilder(resource.getContent().getDateStr()).withSelfRel());
		}
	}
}
