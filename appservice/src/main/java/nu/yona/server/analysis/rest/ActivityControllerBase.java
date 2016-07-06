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
import org.springframework.data.domain.Pageable;
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
import nu.yona.server.analysis.service.ActivityCommentMessageDTO;
import nu.yona.server.analysis.service.ActivityService;
import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.DayActivityOverviewDTO;
import nu.yona.server.analysis.service.WeekActivityDTO;
import nu.yona.server.analysis.service.WeekActivityOverviewDTO;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.messaging.rest.MessageController.MessageResourceAssembler;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.service.GoalIDMapping;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Activity controller base class.
 */
abstract class ActivityControllerBase
{
	@Autowired
	protected ActivityService activityService;

	@Autowired
	protected UserService userService;

	@Autowired
	private CurieProvider curieProvider;

	@Autowired
	private MessageController messageController;

	protected static final String WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT = "/weeks/";
	protected static final String DAY_OVERVIEWS_URI_FRAGMENT = "/days/";
	protected static final String WEEK_ACTIVITY_DETAIL_URI_FRAGMENT = "/weeks/{date}/details/{goalID}";
	protected static final String WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT = WEEK_ACTIVITY_DETAIL_URI_FRAGMENT + "/messages/";
	protected static final String DAY_ACTIVITY_DETAIL_URI_FRAGMENT = "/days/{date}/details/{goalID}";
	protected static final String DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT = DAY_ACTIVITY_DETAIL_URI_FRAGMENT + "/messages/";
	protected static final String GOAL_PATH_VARIABLE = "goalID";
	protected static final String DATE_PATH_VARIABLE = "date";
	protected static final int WEEKS_DEFAULT_PAGE_SIZE = 2;
	protected static final int DAYS_DEFAULT_PAGE_SIZE = 3;
	protected static final int MESSAGES_DEFAULT_PAGE_SIZE = 4;
	protected static final String PREV_REL = "prev"; // IANA reserved, so will not be prefixed
	protected static final String NEXT_REL = "next"; // IANA reserved, so will not be prefixed

	protected HttpEntity<PagedResources<WeekActivityOverviewResource>> getWeekActivityOverviews(Optional<String> password,
			UUID userID, Pageable pageable, PagedResourcesAssembler<WeekActivityOverviewDTO> pagedResourcesAssembler,
			Supplier<Page<WeekActivityOverviewDTO>> activitySupplier, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
						new WeekActivityOverviewResourceAssembler(linkProvider)), HttpStatus.OK));
	}

	protected HttpEntity<PagedResources<DayActivityOverviewResource>> getDayActivityOverviews(Optional<String> password,
			UUID userID, Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityDTO>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDTO<DayActivityDTO>>> activitySupplier, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
						new DayActivityOverviewResourceAssembler(linkProvider)), HttpStatus.OK));
	}

	protected HttpEntity<WeekActivityResource> getWeekActivityDetail(Optional<String> password, UUID userID, String dateStr,
			Function<LocalDate, WeekActivityDTO> activitySupplier, LinkProvider linkProvider)
	{
		LocalDate date = WeekActivityDTO.parseDate(dateStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						new WeekActivityResourceAssembler(linkProvider, true).toResource(activitySupplier.apply(date)),
						HttpStatus.OK));
	}

	protected HttpEntity<DayActivityResource> getDayActivityDetail(Optional<String> password, UUID userID, String dateStr,
			Function<LocalDate, DayActivityDTO> activitySupplier, LinkProvider linkProvider)
	{
		LocalDate date = DayActivityDTO.parseDate(dateStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						new DayActivityResourceAssembler(linkProvider, true, true).toResource(activitySupplier.apply(date)),
						HttpStatus.OK));
	}

	protected HttpEntity<PagedResources<MessageDTO>> getActivityDetailMessages(Optional<String> password, UUID userID,
			Pageable pageable, PagedResourcesAssembler<MessageDTO> pagedResourcesAssembler,
			Supplier<Page<MessageDTO>> messageSupplier, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						pagedResourcesAssembler.toResource(messageSupplier.get(),
								new MessageResourceAssembler(curieProvider, createGoalIDMapping(userID), messageController)),
				HttpStatus.OK));
	}

	protected GoalIDMapping createGoalIDMapping(UUID userID)
	{
		return GoalIDMapping.createInstance(userService.getPrivateUser(userID));
	}

	public abstract void addLinks(GoalIDMapping goalIDMapping, IntervalActivity activity, ActivityCommentMessageDTO message);

	protected void addStandardLinks(GoalIDMapping goalIDMapping, LinkProvider linkProvider, IntervalActivity activity,
			ActivityCommentMessageDTO message)
	{
		if (activity instanceof WeekActivity)
		{
			addWeekDetailsLink(linkProvider, activity, message);
		}
		else
		{
			addDayDetailsLink(linkProvider, activity, message);
		}
		if (!message.getUser().getID().equals(goalIDMapping.getUserID()))
		{
			UUID buddyID = determineBuddyID(goalIDMapping, message);
			addBuddyLink(goalIDMapping.getUserID(), buddyID, message);
		}
		message.getRepliedMessageID().ifPresent(rmid -> addRepliedMessageLink(goalIDMapping.getUserID(), rmid, message));
	}

	private UUID determineBuddyID(GoalIDMapping goalIDMapping, ActivityCommentMessageDTO message)
	{
		return goalIDMapping.getUser().getPrivateData().getBuddies().stream()
				.filter(b -> b.getUser().getID().equals(message.getUser().getID())).map(b -> b.getID()).findAny()
				.orElseThrow(() -> new IllegalArgumentException("User with ID " + message.getUser().getID() + "is not a buddy"));
	}

	private void addWeekDetailsLink(LinkProvider linkProvider, IntervalActivity activity, ActivityCommentMessageDTO message)
	{
		message.add(linkProvider
				.getWeekActivityDetailLinkBuilder(WeekActivityDTO.formatDate(activity.getDate()), activity.getGoal().getID())
				.withRel("weekDetails"));
	}

	private void addDayDetailsLink(LinkProvider linkProvider, IntervalActivity activity, ActivityCommentMessageDTO message)
	{
		message.add(linkProvider
				.getDayActivityDetailLinkBuilder(DayActivityDTO.formatDate(activity.getDate()), activity.getGoal().getID())
				.withRel("dayDetails"));
	}

	private void addRepliedMessageLink(UUID userID, UUID repliedMessageID, ActivityCommentMessageDTO message)
	{
		message.add(MessageController.getAnonymousMessageLinkBuilder(userID, repliedMessageID).withRel("repliedMessage"));
	}

	private void addBuddyLink(UUID userID, UUID buddyID, ActivityCommentMessageDTO message)
	{
		message.add(BuddyController.getBuddyLinkBuilder(userID, buddyID).withRel("buddy"));
	}

	interface LinkProvider
	{
		public ControllerLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalID);

		public ControllerLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalID);

		public ControllerLinkBuilder getGoalLinkBuilder(UUID goalID);

		public ControllerLinkBuilder getDayActivityDetailMessagesLinkBuilder(String dateStr, UUID goalID);

		public Optional<ControllerLinkBuilder> getDayActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalID);

		public ControllerLinkBuilder getWeekActivityDetailMessagesLinkBuilder(String dateStr, UUID goalID);

		public Optional<ControllerLinkBuilder> getWeekActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalID);
	}

	static class WeekActivityResource extends Resource<WeekActivityDTO>
	{
		private final LinkProvider linkProvider;

		public WeekActivityResource(LinkProvider linkProvider, WeekActivityDTO weekActivity)
		{
			super(weekActivity);
			this.linkProvider = linkProvider;
		}

		@JsonFormat(shape = JsonFormat.Shape.OBJECT)
		public Map<DayOfWeek, DayActivityResource> getDayActivities()
		{
			DayActivityResourceAssembler a = new DayActivityResourceAssembler(linkProvider, false, false);
			return getContent().getDayActivities().entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> a.toResource(e.getValue())));
		}
	}

	static class WeekActivityOverviewResource extends Resource<WeekActivityOverviewDTO>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResource(LinkProvider linkProvider, WeekActivityOverviewDTO weekActivityOverview)
		{
			super(weekActivityOverview);
			this.linkProvider = linkProvider;
		}

		public List<WeekActivityResource> getWeekActivities()
		{
			return new WeekActivityResourceAssembler(linkProvider, false).toResources(getContent().getWeekActivities());
		}
	}

	static class DayActivityResource extends Resource<DayActivityDTO>
	{
		public DayActivityResource(DayActivityDTO dayActivity)
		{
			super(dayActivity);
		}

		// TODO: embed messages if included on this detail level
	}

	static class DayActivityOverviewResource extends Resource<DayActivityOverviewDTO<DayActivityDTO>>
	{
		private final LinkProvider linkProvider;

		public DayActivityOverviewResource(LinkProvider linkProvider, DayActivityOverviewDTO<DayActivityDTO> dayActivityOverview)
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
			extends ResourceAssemblerSupport<WeekActivityOverviewDTO, WeekActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public WeekActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, WeekActivityOverviewResource.class);
			this.linkProvider = linkProvider;
		}

		@Override
		public WeekActivityOverviewResource toResource(WeekActivityOverviewDTO weekActivityOverview)
		{
			WeekActivityOverviewResource weekActivityOverviewResource = instantiateResource(weekActivityOverview);
			return weekActivityOverviewResource;
		}

		@Override
		protected WeekActivityOverviewResource instantiateResource(WeekActivityOverviewDTO weekActivityOverview)
		{
			return new WeekActivityOverviewResource(linkProvider, weekActivityOverview);
		}
	}

	static class WeekActivityResourceAssembler extends ResourceAssemblerSupport<WeekActivityDTO, WeekActivityResource>
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
		public WeekActivityResource toResource(WeekActivityDTO weekActivity)
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
		protected WeekActivityResource instantiateResource(WeekActivityDTO weekActivity)
		{
			return new WeekActivityResource(linkProvider, weekActivity);
		}

		private void addWeekDetailsLink(WeekActivityResource weekActivityResource, String rel)
		{
			weekActivityResource.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getDateStr(),
					weekActivityResource.getContent().getGoalID()).withRel(rel));
		}

		private void addGoalLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource
					.add(linkProvider.getGoalLinkBuilder(weekActivityResource.getContent().getGoalID()).withRel("goal"));
		}

		private void addMessagesLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource
					.add(linkProvider.getWeekActivityDetailMessagesLinkBuilder(weekActivityResource.getContent().getDateStr(),
							weekActivityResource.getContent().getGoalID()).withRel("messages"));
		}

		private void addAddCommentLink(WeekActivityResource weekActivityResource)
		{
			Optional<ControllerLinkBuilder> linkBuilder = linkProvider.getWeekActivityDetailAddCommentLinkBuilder(
					weekActivityResource.getContent().getDateStr(), weekActivityResource.getContent().getGoalID());
			linkBuilder.ifPresent(lb -> weekActivityResource.add(lb.withRel("addComment")));
		}

		private void addPrevNextLinks(WeekActivityResource weekActivityResource)
		{
			if (weekActivityResource.getContent().hasPrevious())
			{
				weekActivityResource
						.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getPreviousDateStr(),
								weekActivityResource.getContent().getGoalID()).withRel(PREV_REL));
			}
			if (weekActivityResource.getContent().hasNext())
			{
				weekActivityResource
						.add(linkProvider.getWeekActivityDetailLinkBuilder(weekActivityResource.getContent().getNextDateStr(),
								weekActivityResource.getContent().getGoalID()).withRel(NEXT_REL));
			}
		}
	}

	static class DayActivityResourceAssembler extends ResourceAssemblerSupport<DayActivityDTO, DayActivityResource>
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
		public DayActivityResource toResource(DayActivityDTO dayActivity)
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
		protected DayActivityResource instantiateResource(DayActivityDTO dayActivity)
		{
			return new DayActivityResource(dayActivity);
		}

		private void addGoalLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(linkProvider.getGoalLinkBuilder(dayActivityResource.getContent().getGoalID()).withRel("goal"));
		}

		private void addDayDetailsLink(DayActivityResource dayActivityResource, String rel)
		{
			dayActivityResource.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getDateStr(),
					dayActivityResource.getContent().getGoalID()).withRel(rel));
		}

		private void addMessagesLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(linkProvider.getDayActivityDetailMessagesLinkBuilder(dayActivityResource.getContent().getDateStr(),
							dayActivityResource.getContent().getGoalID()).withRel("messages"));
		}

		private void addAddCommentLink(DayActivityResource dayActivityResource)
		{
			Optional<ControllerLinkBuilder> linkBuilder = linkProvider.getDayActivityDetailAddCommentLinkBuilder(
					dayActivityResource.getContent().getDateStr(), dayActivityResource.getContent().getGoalID());
			linkBuilder.ifPresent(lb -> dayActivityResource.add(lb.withRel("addComment")));
		}

		private void addPrevNextLinks(DayActivityResource dayActivityResource)
		{
			if (dayActivityResource.getContent().hasPrevious())
			{
				dayActivityResource
						.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getPreviousDateStr(),
								dayActivityResource.getContent().getGoalID()).withRel(PREV_REL));
			}
			if (dayActivityResource.getContent().hasNext())
			{
				dayActivityResource
						.add(linkProvider.getDayActivityDetailLinkBuilder(dayActivityResource.getContent().getNextDateStr(),
								dayActivityResource.getContent().getGoalID()).withRel(NEXT_REL));
			}
		}
	}

	static class DayActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<DayActivityOverviewDTO<DayActivityDTO>, DayActivityOverviewResource>
	{
		private final LinkProvider linkProvider;

		public DayActivityOverviewResourceAssembler(LinkProvider linkProvider)
		{
			super(ActivityControllerBase.class, DayActivityOverviewResource.class);
			this.linkProvider = linkProvider;
		}

		@Override
		public DayActivityOverviewResource toResource(DayActivityOverviewDTO<DayActivityDTO> dayActivityOverview)
		{
			DayActivityOverviewResource dayActivityOverviewResource = instantiateResource(dayActivityOverview);
			return dayActivityOverviewResource;
		}

		@Override
		protected DayActivityOverviewResource instantiateResource(DayActivityOverviewDTO<DayActivityDTO> dayActivityOverview)
		{
			return new DayActivityOverviewResource(linkProvider, dayActivityOverview);
		}
	}
}
