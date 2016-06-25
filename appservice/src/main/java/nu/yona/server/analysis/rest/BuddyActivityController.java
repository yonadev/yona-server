package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
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
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.service.ActivityCommentMessageDTO;
import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.DayActivityOverviewDTO;
import nu.yona.server.analysis.service.PostPutActivityCommentMessageDTO;
import nu.yona.server.analysis.service.WeekActivityDTO;
import nu.yona.server.analysis.service.WeekActivityOverviewDTO;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.messaging.rest.MessageController;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.GoalIDMapping;

/*
 * Controller to retrieve activity data for a user.
 */
@Controller
@RequestMapping(value = "/users/{userID}/buddies/{buddyID}/activity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BuddyActivityController extends ActivityControllerBase
{
	@Autowired
	private BuddyService buddyService;

	@Autowired
	private MessageController messageController;

	@RequestMapping(value = WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<WeekActivityOverviewResource>> getBuddyWeekActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PageableDefault(size = WEEKS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<WeekActivityOverviewDTO> pagedResourcesAssembler)
	{
		return getWeekActivityOverviews(password, userID, pageable, pagedResourcesAssembler,
				() -> activityService.getBuddyWeekActivityOverviews(buddyID, pageable),
				new BuddyActivityLinkProvider(buddyService, userID, buddyID));
	}

	@RequestMapping(value = DAY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewResource>> getBuddyDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityDTO>> pagedResourcesAssembler)
	{
		return getDayActivityOverviews(password, userID, pageable, pagedResourcesAssembler,
				() -> activityService.getBuddyDayActivityOverviews(buddyID, pageable),
				new BuddyActivityLinkProvider(buddyService, userID, buddyID));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<WeekActivityResource> getBuddyWeekActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID)
	{
		return getWeekActivityDetail(password, userID, dateStr,
				date -> activityService.getBuddyWeekActivityDetail(buddyID, date, goalID),
				new BuddyActivityLinkProvider(buddyService, userID, buddyID));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageDTO>> getBuddyWeekActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDTO> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(password, userID, pageable,
				pagedResourcesAssembler, () -> activityService.getBuddyWeekActivityDetailMessages(userID, buddyID,
						WeekActivityDTO.parseDate(dateStr), goalID, pageable),
				new BuddyActivityLinkProvider(buddyService, userID, buddyID));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<MessageDTO> addBuddyWeekActivityDetailMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID, @RequestBody PostPutActivityCommentMessageDTO newMessage)
	{
		return CryptoSession
				.execute(password,
						() -> userService
								.canAccessPrivateData(
										userID),
						() -> new ResponseEntity<>(messageController.toMessageResource(createGoalIDMapping(userID),
								activityService.addMessageToWeekActivity(userID, buddyID, WeekActivityDTO.parseDate(dateStr),
										goalID, newMessage)),
								HttpStatus.OK));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<DayActivityResource> getBuddyDayActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID)
	{
		return getDayActivityDetail(password, userID, dateStr,
				date -> activityService.getBuddyDayActivityDetail(buddyID, date, goalID),
				new BuddyActivityLinkProvider(buddyService, userID, buddyID));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<MessageDTO>> getBuddyDayActivityDetailMessages(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID,
			@PageableDefault(size = MESSAGES_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<MessageDTO> pagedResourcesAssembler)
	{
		return getActivityDetailMessages(password, userID, pageable,
				pagedResourcesAssembler, () -> activityService.getBuddyDayActivityDetailMessages(userID, buddyID,
						DayActivityDTO.parseDate(dateStr), goalID, pageable),
				new BuddyActivityLinkProvider(buddyService, userID, buddyID));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_MESSAGES_URI_FRAGMENT, method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<MessageDTO> addBuddyDayActivityDetailMessage(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID buddyID, @PathVariable(value = DATE_PATH_VARIABLE) String dateStr,
			@PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID, @RequestBody PostPutActivityCommentMessageDTO newMessage)
	{
		return CryptoSession
				.execute(password,
						() -> userService
								.canAccessPrivateData(
										userID),
						() -> new ResponseEntity<>(messageController.toMessageResource(createGoalIDMapping(userID),
								activityService.addMessageToDayActivity(userID, buddyID, DayActivityDTO.parseDate(dateStr),
										goalID, newMessage)),
								HttpStatus.OK));
	}

	@Override
	public void addLinks(GoalIDMapping goalIDMapping, IntervalActivity activity, ActivityCommentMessageDTO message)
	{
		LinkProvider linkProvider = new BuddyActivityLinkProvider(buddyService, goalIDMapping.getUserID(),
				goalIDMapping.getBuddyID(activity.getGoal().getID()));
		addStandardLinks(goalIDMapping, linkProvider, activity, message);
	}

	public static ControllerLinkBuilder getBuddyDayActivityOverviewsLinkBuilder(UUID userID, UUID buddyID)
	{
		BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
		return linkTo(methodOn.getBuddyDayActivityOverviews(null, userID, buddyID, null, null));
	}

	public static ControllerLinkBuilder getBuddyWeekActivityOverviewsLinkBuilder(UUID userID, UUID buddyID)
	{
		BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
		return linkTo(methodOn.getBuddyWeekActivityOverviews(null, userID, buddyID, null, null));
	}

	public static ControllerLinkBuilder getBuddyDayActivityDetailLinkBuilder(UUID userID, UUID buddyID, String dateStr,
			UUID goalID)
	{
		BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
		return linkTo(methodOn.getBuddyDayActivityDetail(null, userID, buddyID, dateStr, goalID));
	}

	private static final class BuddyActivityLinkProvider implements LinkProvider
	{
		private final BuddyService buddyService;
		private final UUID userID;
		private final UUID buddyID;

		public BuddyActivityLinkProvider(BuddyService buddyService, UUID userID, UUID buddyID)
		{
			this.buddyService = buddyService;
			this.userID = userID;
			this.buddyID = buddyID;
		}

		@Override
		public ControllerLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalID)
		{
			return getBuddyDayActivityDetailLinkBuilder(userID, buddyID, dateStr, goalID);
		}

		@Override
		public ControllerLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalID)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return linkTo(methodOn.getBuddyWeekActivityDetail(null, userID, buddyID, dateStr, goalID));
		}

		@Override
		public ControllerLinkBuilder getGoalLinkBuilder(UUID goalID)
		{
			return GoalController.getGoalLinkBuilder(buddyService.getBuddy(buddyID).getUser().getID(), goalID);
		}

		@Override
		public ControllerLinkBuilder getDayActivityDetailMessagesLinkBuilder(String dateStr, UUID goalID)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return linkTo(
					methodOn.getBuddyDayActivityDetailMessages(Optional.empty(), userID, buddyID, dateStr, goalID, null, null));
		}

		@Override
		public Optional<ControllerLinkBuilder> getDayActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalID)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return Optional.of(
					linkTo(methodOn.addBuddyDayActivityDetailMessage(Optional.empty(), userID, buddyID, dateStr, goalID, null)));
		}

		@Override
		public ControllerLinkBuilder getWeekActivityDetailMessagesLinkBuilder(String dateStr, UUID goalID)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return linkTo(
					methodOn.getBuddyWeekActivityDetailMessages(Optional.empty(), userID, buddyID, dateStr, goalID, null, null));
		}

		@Override
		public Optional<ControllerLinkBuilder> getWeekActivityDetailAddCommentLinkBuilder(String dateStr, UUID goalID)
		{
			BuddyActivityController methodOn = methodOn(BuddyActivityController.class);
			return Optional.of(
					linkTo(methodOn.addBuddyWeekActivityDetailMessage(Optional.empty(), userID, buddyID, dateStr, goalID, null)));
		}
	}
}