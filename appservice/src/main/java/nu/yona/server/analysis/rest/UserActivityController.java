package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.subscriptions.rest.BuddyController;
import nu.yona.server.subscriptions.rest.UserController;
import nu.yona.server.subscriptions.service.UserDTO;

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
	public HttpEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PageableDefault(size = DAYS_DEFAULT_PAGE_SIZE) Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> pagedResourcesAssembler)
	{
		return getDayActivityOverviewsWithBuddies(password, userID, pageable, pagedResourcesAssembler,
				() -> activityService.getUserAndBuddiesDayActivityOverviews(userID, pageable),
				new UserActivityLinkProvider(userID));
	}

	private HttpEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(
			Optional<String> password, UUID userID, Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>> activitySupplier, LinkProvider linkProvider)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> getDayActivityOverviewsWithBuddies(userID, pagedResourcesAssembler, activitySupplier));
	}

	private ResponseEntity<PagedResources<DayActivityOverviewWithBuddiesResource>> getDayActivityOverviewsWithBuddies(UUID userID,
			PagedResourcesAssembler<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>> pagedResourcesAssembler,
			Supplier<Page<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>> activitySupplier)
	{
		GoalIDMapping goalIDMapping = GoalIDMapping.createInstance(userService.getPrivateUser(userID));
		return new ResponseEntity<>(pagedResourcesAssembler.toResource(activitySupplier.get(),
				new DayActivityOverviewWithBuddiesResourceAssembler(goalIDMapping)), HttpStatus.OK);
	}

	private static class GoalIDMapping
	{
		private final UUID userID;
		private final Set<UUID> userGoalIDs;
		private final Map<UUID, UUID> goalIDToBuddyIDmapping;

		private GoalIDMapping(UUID userID, Set<UUID> userGoalIDs, Map<UUID, UUID> goalIDToBuddyIDmapping)
		{
			this.userID = userID;
			this.userGoalIDs = userGoalIDs;
			this.goalIDToBuddyIDmapping = goalIDToBuddyIDmapping;
		}

		public UUID getUserID()
		{
			return userID;
		}

		public boolean isUserGoal(UUID goalID)
		{
			return userGoalIDs.contains(goalID);
		}

		public UUID getBuddyID(UUID goalID)
		{
			UUID uuid = goalIDToBuddyIDmapping.get(goalID);
			if (uuid == null)
			{
				throw new IllegalArgumentException("Goal " + goalID + " not found");
			}
			return uuid;
		}

		static GoalIDMapping createInstance(UserDTO user)
		{
			UUID userID = user.getID();
			Set<UUID> userGoalIDs = user.getPrivateData().getGoals().stream().map(GoalDTO::getID).collect(Collectors.toSet());
			Map<UUID, UUID> goalIDToBuddyIDmapping = new HashMap<>();
			user.getPrivateData().getBuddies()
					.forEach(b -> b.getGoals().forEach(g -> goalIDToBuddyIDmapping.put(g.getID(), b.getID())));
			return new GoalIDMapping(userID, userGoalIDs, goalIDToBuddyIDmapping);
		}
	}

	public static ControllerLinkBuilder getUserDayActivityOverviewsLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityOverviews(null, userID, null, null));
	}

	public static ControllerLinkBuilder getDayActivityOverviewsWithBuddiesLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getDayActivityOverviewsWithBuddies(null, userID, null, null));
	}

	public static ControllerLinkBuilder getUserWeekActivityOverviewsLinkBuilder(UUID userID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserWeekActivityOverviews(null, userID, null, null));
	}

	public static ControllerLinkBuilder getUserDayActivityDetailLinkBuilder(UUID userID, String dateStr, UUID goalID)
	{
		UserActivityController methodOn = methodOn(UserActivityController.class);
		return linkTo(methodOn.getUserDayActivityDetail(null, userID, dateStr, goalID));
	}

	static class DayActivityOverviewWithBuddiesResource extends Resource<DayActivityOverviewDTO<DayActivityWithBuddiesDTO>>
	{
		private final GoalIDMapping goalIDMapping;

		public DayActivityOverviewWithBuddiesResource(GoalIDMapping goalIDMapping,
				DayActivityOverviewDTO<DayActivityWithBuddiesDTO> dayActivityOverview)
		{
			super(dayActivityOverview);
			this.goalIDMapping = goalIDMapping;
		}

		public List<DayActivityWithBuddiesResource> getDayActivities()
		{
			return new DayActivityWithBuddiesResourceAssembler(goalIDMapping, getContent().getDate())
					.toResources(getContent().getDayActivities());
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
			return UserActivityController.getUserDayActivityDetailLinkBuilder(userID, dateStr, goalID);
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
		private final GoalIDMapping goalIDMapping;

		public DayActivityOverviewWithBuddiesResourceAssembler(GoalIDMapping goalIDMapping)
		{
			super(ActivityControllerBase.class, DayActivityOverviewWithBuddiesResource.class);
			this.goalIDMapping = goalIDMapping;
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
			return new DayActivityOverviewWithBuddiesResource(goalIDMapping, dayActivityOverview);
		}
	}

	static class DayActivityWithBuddiesResource extends Resource<DayActivityWithBuddiesDTO>
	{
		private final GoalIDMapping goalIDMapping;
		private final String dateStr;

		public DayActivityWithBuddiesResource(GoalIDMapping goalIDMapping, String dateStr, DayActivityWithBuddiesDTO dayActivity)
		{
			super(dayActivity);
			this.goalIDMapping = goalIDMapping;
			this.dateStr = dateStr;
		}

		@JsonInclude
		public List<ActivityForOneUserResource> getDayActivitiesForUsers()
		{
			return new ActivityForOneUserResourceAssembler(goalIDMapping, dateStr)
					.toResources(getContent().getDayActivitiesForUsers());
		}
	}

	static class DayActivityWithBuddiesResourceAssembler
			extends ResourceAssemblerSupport<DayActivityWithBuddiesDTO, DayActivityWithBuddiesResource>
	{
		private final GoalIDMapping goalIDMapping;
		private final String dateStr;

		public DayActivityWithBuddiesResourceAssembler(GoalIDMapping goalIDMapping, String dateStr)
		{
			super(ActivityControllerBase.class, DayActivityWithBuddiesResource.class);
			this.goalIDMapping = goalIDMapping;
			this.dateStr = dateStr;
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
			return new DayActivityWithBuddiesResource(goalIDMapping, dateStr, dayActivity);
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
		private final GoalIDMapping goalIDMapping;
		private final String dateStr;

		public ActivityForOneUserResourceAssembler(GoalIDMapping goalIDMapping, String dateStr)
		{
			super(ActivityControllerBase.class, ActivityForOneUserResource.class);
			this.goalIDMapping = goalIDMapping;
			this.dateStr = dateStr;
		}

		@Override
		public ActivityForOneUserResource toResource(ActivityForOneUser dayActivity)
		{
			ActivityForOneUserResource dayActivityResource = instantiateResource(dayActivity);

			UUID goalID = dayActivity.getGoalID();
			UUID userID = goalIDMapping.getUserID();
			if (goalIDMapping.isUserGoal(goalID))
			{
				addGoalLinkForUser(userID, goalID, dayActivityResource);
				addDayDetailsLinkForUser(userID, goalID, dayActivityResource);
				addUserLink(userID, dayActivityResource);
			}
			else
			{
				UUID buddyID = goalIDMapping.getBuddyID(goalID);
				addGoalLinkForBuddy(userID, buddyID, goalID, dayActivityResource);
				addDayDetailsLinkForBuddy(userID, buddyID, goalID, dayActivityResource);
				addBuddyLink(userID, buddyID, dayActivityResource);
			}
			return dayActivityResource;
		}

		@Override
		protected ActivityForOneUserResource instantiateResource(ActivityForOneUser dayActivity)
		{
			return new ActivityForOneUserResource(dayActivity);
		}

		private void addGoalLinkForUser(UUID userID, UUID goalID, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(GoalController.getGoalLinkBuilder(userID, goalID).withRel("goal"));
		}

		private void addDayDetailsLinkForUser(UUID userID, UUID goalID, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(
					UserActivityController.getUserDayActivityDetailLinkBuilder(userID, dateStr, goalID).withRel("dayDetails"));
		}

		private void addUserLink(UUID userID, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(UserController.getPrivateUserLink("user", userID));
		}

		private void addGoalLinkForBuddy(UUID userID, UUID buddyID, UUID goalID, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(BuddyController.getGoalLinkBuilder(userID, buddyID, goalID).withRel("goal"));
		}

		private void addDayDetailsLinkForBuddy(UUID userID, UUID buddyID, UUID goalID,
				ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(BuddyActivityController.getBuddyDayActivityDetailLinkBuilder(userID, buddyID, dateStr, goalID)
					.withRel("dayDetails"));
		}

		private void addBuddyLink(UUID userID, UUID buddyID, ActivityForOneUserResource dayActivityResource)
		{
			dayActivityResource.add(BuddyController.getBuddyLinkBuilder(userID, buddyID).withRel("buddy"));
		}
	}
}