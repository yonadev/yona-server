package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.analysis.service.ActivityService;
import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.DayActivityOverviewDTO;
import nu.yona.server.analysis.service.WeekActivityDTO;
import nu.yona.server.analysis.service.WeekActivityOverviewDTO;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.rest.GoalController;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Controller to retrieve activity data for a user.
 */
@Controller
@RequestMapping(value = "/users/{userID}/activity")
public class ActivityController
{
	@Autowired
	private ActivityService activityService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurieProvider curieProvider;

	@RequestMapping(value = "/weeks/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<WeekActivityOverviewResource>> getWeekActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, Pageable pageable,
			PagedResourcesAssembler<WeekActivityOverviewDTO> pagedResourcesAssembler)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						pagedResourcesAssembler.toResource(activityService.getWeekActivityOverviews(userID, pageable),
								new WeekActivityOverviewResourceAssembler(curieProvider, userID)),
						HttpStatus.OK));
	}

	@RequestMapping(value = "/days/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityOverviewResource>> getDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, Pageable pageable,
			PagedResourcesAssembler<DayActivityOverviewDTO> pagedResourcesAssembler)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						pagedResourcesAssembler.toResource(activityService.getDayActivityOverviews(userID, pageable),
								new DayActivityOverviewResourceAssembler(curieProvider, userID)),
						HttpStatus.OK));
	}

	@RequestMapping(value = "/weeks/{week}/details/{goalID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<WeekActivityResource> getWeekActivityDetail(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable(value = "week") String weekStr, @PathVariable(value = "goalID") UUID goalID)
	{
		LocalDate date = WeekActivityDTO.parseDate(weekStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(new WeekActivityResourceAssembler(curieProvider, userID)
						.toResource(activityService.getWeekActivityDetail(userID, date, goalID)), HttpStatus.OK));
	}

	@RequestMapping(value = "/days/{date}/details/{goalID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<DayActivityResource> getDayActivityDetail(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable(value = "date") String dateStr, @PathVariable(value = "goalID") UUID goalID)
	{
		LocalDate date = DayActivityDTO.parseDate(dateStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(new DayActivityResourceAssembler(userID, true)
						.toResource(activityService.getDayActivityDetail(userID, date, goalID)), HttpStatus.OK));
	}

	public static ControllerLinkBuilder getDayActivityOverviewsLinkBuilder(UUID userID)
	{
		ActivityController methodOn = methodOn(ActivityController.class);
		return linkTo(methodOn.getDayActivityOverviews(null, userID, null, null));
	}

	public static ControllerLinkBuilder getWeekActivityOverviewsLinkBuilder(UUID userID)
	{
		ActivityController methodOn = methodOn(ActivityController.class);
		return linkTo(methodOn.getWeekActivityOverviews(null, userID, null, null));
	}

	static ControllerLinkBuilder getDayActivityDetailLinkBuilder(UUID userID, String dateStr, UUID goalID)
	{
		ActivityController methodOn = methodOn(ActivityController.class);
		return linkTo(methodOn.getDayActivityDetail(null, userID, dateStr, goalID));
	}

	static ControllerLinkBuilder getWeekActivityDetailLinkBuilder(UUID userID, String weekStr, UUID goalID)
	{
		ActivityController methodOn = methodOn(ActivityController.class);
		return linkTo(methodOn.getWeekActivityDetail(null, userID, weekStr, goalID));
	}

	static class WeekActivityResource extends Resource<WeekActivityDTO>
	{
		private final CurieProvider curieProvider;
		private final UUID userID;

		public WeekActivityResource(CurieProvider curieProvider, UUID userID, WeekActivityDTO weekActivity)
		{
			super(weekActivity);
			this.curieProvider = curieProvider;
			this.userID = userID;
		}

		@JsonProperty("_embedded")
		public Map<String, List<DayActivityResource>> getEmbeddedResources()
		{
			HashMap<String, List<DayActivityResource>> result = new HashMap<String, List<DayActivityResource>>();
			result.put(curieProvider.getNamespacedRelFor("dayActivities"),
					new DayActivityResourceAssembler(userID, false).toResources(getContent().getDayActivities()));
			return result;
		}
	}

	static class WeekActivityOverviewResource extends Resource<WeekActivityOverviewDTO>
	{
		private final CurieProvider curieProvider;
		private final UUID userID;

		public WeekActivityOverviewResource(CurieProvider curieProvider, UUID userID,
				WeekActivityOverviewDTO weekActivityOverview)
		{
			super(weekActivityOverview);
			this.curieProvider = curieProvider;
			this.userID = userID;
		}

		@JsonProperty("_embedded")
		public Map<String, List<WeekActivityResource>> getEmbeddedResources()
		{
			HashMap<String, List<WeekActivityResource>> result = new HashMap<String, List<WeekActivityResource>>();
			result.put(curieProvider.getNamespacedRelFor("weekActivities"),
					new WeekActivityResourceAssembler(curieProvider, userID).toResources(getContent().getWeekActivities()));
			return result;
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

	static class DayActivityOverviewResource extends Resource<DayActivityOverviewDTO>
	{
		private final CurieProvider curieProvider;
		private final UUID userID;

		public DayActivityOverviewResource(CurieProvider curieProvider, UUID userID, DayActivityOverviewDTO dayActivityOverview)
		{
			super(dayActivityOverview);
			this.curieProvider = curieProvider;
			this.userID = userID;
		}

		@JsonProperty("_embedded")
		public Map<String, List<DayActivityResource>> getEmbeddedResources()
		{
			HashMap<String, List<DayActivityResource>> result = new HashMap<String, List<DayActivityResource>>();
			result.put(curieProvider.getNamespacedRelFor("dayActivities"),
					new DayActivityResourceAssembler(userID, true).toResources(getContent().getWeekActivities()));
			return result;
		}
	}

	static class WeekActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<WeekActivityOverviewDTO, WeekActivityOverviewResource>
	{
		private final CurieProvider curieProvider;
		private final UUID userID;

		public WeekActivityOverviewResourceAssembler(CurieProvider curieProvider, UUID userID)
		{
			super(ActivityController.class, WeekActivityOverviewResource.class);
			this.curieProvider = curieProvider;
			this.userID = userID;
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
			return new WeekActivityOverviewResource(curieProvider, userID, weekActivityOverview);
		}
	}

	static class WeekActivityResourceAssembler extends ResourceAssemblerSupport<WeekActivityDTO, WeekActivityResource>
	{
		private final CurieProvider curieProvider;
		private final UUID userID;

		public WeekActivityResourceAssembler(CurieProvider curieProvider, UUID userID)
		{
			super(ActivityController.class, WeekActivityResource.class);
			this.curieProvider = curieProvider;
			this.userID = userID;
		}

		@Override
		public WeekActivityResource toResource(WeekActivityDTO weekActivity)
		{
			WeekActivityResource weekActivityResource = instantiateResource(weekActivity);
			addSelfLink(weekActivityResource);
			addGoalLink(weekActivityResource);
			return weekActivityResource;
		}

		@Override
		protected WeekActivityResource instantiateResource(WeekActivityDTO weekActivity)
		{
			return new WeekActivityResource(curieProvider, userID, weekActivity);
		}

		private void addSelfLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource.add(getWeekActivityDetailLinkBuilder(userID, weekActivityResource.getContent().getDate(),
					weekActivityResource.getContent().getGoalID()).withSelfRel());
		}

		private void addGoalLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource.add(
					GoalController.getGoalLinkBuilder(userID, weekActivityResource.getContent().getGoalID()).withRel("goal"));
		}
	}

	static class DayActivityResourceAssembler extends ResourceAssemblerSupport<DayActivityDTO, DayActivityResource>
	{
		private final UUID userID;
		private final boolean addGoalLink;

		public DayActivityResourceAssembler(UUID userID, boolean addGoalLink)
		{
			super(ActivityController.class, DayActivityResource.class);
			this.userID = userID;
			this.addGoalLink = addGoalLink;
		}

		@Override
		public DayActivityResource toResource(DayActivityDTO dayActivity)
		{
			DayActivityResource dayActivityResource = instantiateResource(dayActivity);
			addSelfLink(dayActivityResource);
			if (addGoalLink)
				addGoalLink(dayActivityResource);
			return dayActivityResource;
		}

		@Override
		protected DayActivityResource instantiateResource(DayActivityDTO dayActivity)
		{
			return new DayActivityResource(dayActivity);
		}

		private void addSelfLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource.add(getDayActivityDetailLinkBuilder(userID, dayActivityResource.getContent().getDate(),
					dayActivityResource.getContent().getGoalID()).withSelfRel());
		}

		private void addGoalLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(GoalController.getGoalLinkBuilder(userID, dayActivityResource.getContent().getGoalID()).withRel("goal"));
		}
	}

	static class DayActivityOverviewResourceAssembler
			extends ResourceAssemblerSupport<DayActivityOverviewDTO, DayActivityOverviewResource>
	{
		private final CurieProvider curieProvider;
		private final UUID userID;

		public DayActivityOverviewResourceAssembler(CurieProvider curieProvider, UUID userID)
		{
			super(ActivityController.class, DayActivityOverviewResource.class);
			this.curieProvider = curieProvider;
			this.userID = userID;
		}

		@Override
		public DayActivityOverviewResource toResource(DayActivityOverviewDTO dayActivityOverview)
		{
			DayActivityOverviewResource dayActivityOverviewResource = instantiateResource(dayActivityOverview);
			return dayActivityOverviewResource;
		}

		@Override
		protected DayActivityOverviewResource instantiateResource(DayActivityOverviewDTO dayActivityOverview)
		{
			return new DayActivityOverviewResource(curieProvider, userID, dayActivityOverview);
		}
	}
}
