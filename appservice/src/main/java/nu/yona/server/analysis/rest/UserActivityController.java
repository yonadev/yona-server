package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

import nu.yona.server.analysis.service.DayActivityOverviewDTO;
import nu.yona.server.analysis.service.WeekActivityOverviewDTO;
import nu.yona.server.goals.rest.GoalController;

/*
 * Controller to retrieve activity data for a user.
 */
@Controller
@RequestMapping(value = "/users/{userID}/activity", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserActivityController extends ActivityControllerBase
{
	@RequestMapping(value = WEEK_ACTIVITY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<WeekActivityOverviewResource>> getUserWeekActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, Pageable pageable,
			PagedResourcesAssembler<WeekActivityOverviewDTO> pagedResourcesAssembler)
	{
		return getWeekActivityOverviews(password, userID, pageable, pagedResourcesAssembler,
				() -> activityService.getUserWeekActivityOverviews(userID, pageable), new UserActivityLinkProvider(userID));
	}

	@RequestMapping(value = DAY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewResource>> getUserDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO> pagedResourcesAssembler)
	{
		return getDayActivityOverviews(password, userID, pageable, pagedResourcesAssembler,
				() -> activityService.getUserDayActivityOverviews(userID, pageable), new UserActivityLinkProvider(userID));
	}

	@RequestMapping(value = WEEK_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<WeekActivityResource> getUserWeekActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID)
	{
		return getWeekActivityDetail(password, userID, dateStr,
				date -> activityService.getUserWeekActivityDetail(userID, date, goalID), new UserActivityLinkProvider(userID));
	}

	@RequestMapping(value = DAY_ACTIVITY_DETAIL_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<DayActivityResource> getUserDayActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable(value = DATE_PATH_VARIABLE) String dateStr, @PathVariable(value = GOAL_PATH_VARIABLE) UUID goalID)
	{
		return getDayActivityDetail(password, userID, dateStr,
				date -> activityService.getUserDayActivityDetail(userID, date, goalID), new UserActivityLinkProvider(userID));
	}

	public static ControllerLinkBuilder getUserDayActivityOverviewsLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityOverviews(null, userID, null, null));
	}

	public static ControllerLinkBuilder getUserWeekActivityOverviewsLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserWeekActivityOverviews(null, userID, null, null));
	}

	static final class UserActivityLinkProvider implements LinkProvider
	{
		private final UUID userID;

		public UserActivityLinkProvider(UUID userID)
		{
			this.userID = userID;
		}

		@Override
		public ControllerLinkBuilder getDayActivityDetailLinkBuilder(String dateStr, UUID goalID)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserDayActivityDetail(null, userID, dateStr, goalID));
		}

		@Override
		public ControllerLinkBuilder getWeekActivityDetailLinkBuilder(String dateStr, UUID goalID)
		{
			UserActivityController methodOn = methodOn(UserActivityController.class);
			return linkTo(methodOn.getUserWeekActivityDetail(null, userID, dateStr, goalID));
		}

		@Override
		public ControllerLinkBuilder getGoalLinkBuilder(UUID goalID)
		{
			return GoalController.getGoalLinkBuilder(userID, goalID);
		}
	}
}