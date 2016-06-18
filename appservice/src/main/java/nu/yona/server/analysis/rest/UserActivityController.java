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

import com.fasterxml.jackson.annotation.JsonInclude;

import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.DayActivityOverviewDTO;
import nu.yona.server.analysis.service.DayActivityWithBuddiesDTO;
import nu.yona.server.analysis.service.DayActivityWithBuddiesDTO.ActivityForOneUser;
import nu.yona.server.analysis.service.WeekActivityOverviewDTO;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.rest.ActivityCategoryController;
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
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PageableDefault(size = WEEKS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<WeekActivityOverviewDTO> pagedResourcesAssembler)
	{
		return getWeekActivityOverviews(password, userID, pageable, pagedResourcesAssembler,
				() -> activityService.getUserWeekActivityOverviews(userID, pageable), new UserActivityLinkProvider(userID));
	}

	@RequestMapping(value = DAY_OVERVIEWS_URI_FRAGMENT, method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewResource>> getUserDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityDTO>> pagedResourcesAssembler)
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

	@RequestMapping(value = "/withBuddies/days/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getUserAndBuddiesDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> pagedResourcesAssembler)
	{
		return getDayActivityOverviewsUAndB(password, userID, pageable, pagedResourcesAssembler,
				() -> activityService.getUserAndBuddiesDayActivityOverviews(userID, pageable),
				new UserActivityLinkProvider(userID));
	}

	private HttpEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsUAndB(
			Optional<String> password, UUID userID, Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>> activitySupplier, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> new ResponseEntity<>(
				pagedResourcesAssembler.toResource(activitySupplier.get(), new DayActivityOverviewWithBuddiesResourceAssembler()),
				HttpStatus.OK));
	}

	public static ControllerLinkBuilder getUserDayActivityOverviewsLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityOverviews(null, userID, null, null));
	}

	public static ControllerLinkBuilder getDayActivityOverviewsWithBuddiesLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserAndBuddiesDayActivityOverviews(null, userID, null, null));
	}

	public static ControllerLinkBuilder getUserWeekActivityOverviewsLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserWeekActivityOverviews(null, userID, null, null));
	}

	static class DayActivityOverviewWithBuddiesResource extends Resource<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>
	{
		public DayActivityOverviewWithBuddiesResource(DayActivityOverviewDTO<DayActivityWithBuddiesDTO> dayActivityOverview)
		{
			super(dayActivityOverview);
		}

		public List<DayActivityWithBuddiesResource> getDayActivities()
		{
			return new DayActivityWithBuddiesResourceAssembler().toResources(getContent().getDayActivities());
		}
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

	static class DayActivityOverviewWithBuddiesResourceAssembler extends
			ResourceAssemblerSupport<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>, DayActivityOverviewWithBuddiesResource>
	{
		public DayActivityOverviewWithBuddiesResourceAssembler()
		{
			super(ActivityControllerBase.class, DayActivityOverviewWithBuddiesResource.class);
		}

		@Override
		public DayActivityOverviewWithBuddiesResource toResource(
				DayActivityOverviewDTO<DayActivityWithBuddiesDTO> dayActivityOverview)
		{
			return instantiateResource(dayActivityOverview);
		}

		@Override
		protected DayActivityOverviewWithBuddiesResource instantiateResource(
				DayActivityOverviewDTO<DayActivityWithBuddiesDTO> dayActivityOverview)
		{
			return new DayActivityOverviewWithBuddiesResource(dayActivityOverview);
		}
	}

	static class DayActivityWithBuddiesResource extends Resource<DayActivityWithBuddiesDTO>
	{
		public DayActivityWithBuddiesResource(DayActivityWithBuddiesDTO dayActivity)
		{
			super(dayActivity);
		}

		@JsonInclude
		public List<ActivityForOneUserResource> getDayActivitiesForUsers()
		{
			return new ActivityForOneUserResourceAssembler().toResources(getContent().getDayActivitiesForUsers());
		}
	}

	static class DayActivityWithBuddiesResourceAssembler
			extends ResourceAssemblerSupport<DayActivityWithBuddiesDTO, DayActivityWithBuddiesResource>
	{
		public DayActivityWithBuddiesResourceAssembler()
		{
			super(ActivityControllerBase.class, DayActivityWithBuddiesResource.class);
		}

		@Override
		public DayActivityWithBuddiesResource toResource(DayActivityWithBuddiesDTO dayActivity)
		{
			DayActivityWithBuddiesResource dayActivityResource = instantiateResource(dayActivity);
			addActivityCategoryLink(dayActivityResource);
			return dayActivityResource;
		}

		@Override
		protected DayActivityWithBuddiesResource instantiateResource(DayActivityWithBuddiesDTO dayActivity)
		{
			return new DayActivityWithBuddiesResource(dayActivity);
		}

		private void addActivityCategoryLink(DayActivityWithBuddiesResource dayActivityResource)
		{
			dayActivityResource.add(ActivityCategoryController
					.getActivityCategoryLinkBuilder(dayActivityResource.getContent().getActivityCategoryID())
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
		public ActivityForOneUserResourceAssembler()
		{
			super(ActivityControllerBase.class, ActivityForOneUserResource.class);
		}

		@Override
		public ActivityForOneUserResource toResource(ActivityForOneUser dayActivity)
		{
			ActivityForOneUserResource dayActivityResource = instantiateResource(dayActivity);
			/*
			 * - $ref: '#/definitions/GoalLink' - $ref: '#/definitions/DayDetailsLink' - $ref: '#/definitions/UserLink' - $ref:
			 * '#/definitions/BuddyLink'
			 */
			return dayActivityResource;
		}

		@Override
		protected ActivityForOneUserResource instantiateResource(ActivityForOneUser dayActivity)
		{
			return new ActivityForOneUserResource(dayActivity);
		}
		/*
		 * TODO: Build goal link based on user ID or buddy ID, depending on the activity. private void
		 * addGoalLink(ActivityForOneUserResource dayActivityResource) { dayActivityResource
		 * .add(GoalController.getGoalLinkBuilder(dayActivityResource.getContent().getGoalID()).withRel("goal")); }
		 */ }
}