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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.DayActivityOverviewDTO;
import nu.yona.server.analysis.service.WeekActivityOverviewDTO;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.subscriptions.service.BuddyService;

/*
 * Controller to retrieve activity data for a user.
 */
@Controller
@RequestMapping(value = "/users/{userID}/buddies/{buddyID}/activity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BuddyActivityController extends ActivityControllerBase
{
	@Autowired
	private BuddyService buddyService;

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
	}
}