package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.analysis.service.AnalysisEngineService;
import nu.yona.server.analysis.service.DayActivityDTO;
import nu.yona.server.analysis.service.WeekActivityDTO;
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
	private AnalysisEngineService analysisEngineService;

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/weeks/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<WeekActivityResource>> getWeekActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, Pageable pageable,
			PagedResourcesAssembler<WeekActivityDTO> pagedResourcesAssembler)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						pagedResourcesAssembler.toResource(analysisEngineService.getWeekActivity(userID, pageable),
								new WeekActivityResourceAssembler(userID)),
						HttpStatus.OK));
	}

	@RequestMapping(value = "/days/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<DayActivityResource>> getDayActivityOverviews(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID, Pageable pageable,
			PagedResourcesAssembler<DayActivityDTO> pagedResourcesAssembler)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<>(
						pagedResourcesAssembler.toResource(analysisEngineService.getDayActivity(userID, pageable),
								new DayActivityResourceAssembler(userID)),
						HttpStatus.OK));
	}

	@RequestMapping(value = "/weeks/{week}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<WeekActivityResource> getWeekActivity(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable(value = "week") String weekStr, @RequestParam(value = "goal") UUID goalID)
	{
		LocalDate date = WeekActivityDTO.parseDate(weekStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> new ResponseEntity<>(
				new WeekActivityResourceAssembler(userID).toResource(analysisEngineService.getWeekActivity(userID, date, goalID)),
				HttpStatus.OK));
	}

	@RequestMapping(value = "/days/{date}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<DayActivityResource> getDayActivity(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable(value = "date") String dateStr, @RequestParam(value = "goal") UUID goalID)
	{
		LocalDate date = DayActivityDTO.parseDate(dateStr);
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> new ResponseEntity<>(
				new DayActivityResourceAssembler(userID).toResource(analysisEngineService.getDayActivity(userID, date, goalID)),
				HttpStatus.OK));
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

	static ControllerLinkBuilder getDayActivityLinkBuilder(UUID userID, String dateStr, UUID goalID)
	{
		ActivityController methodOn = methodOn(ActivityController.class);
		return linkTo(methodOn.getDayActivity(null, userID, dateStr, goalID));
	}

	static ControllerLinkBuilder getWeekActivityLinkBuilder(UUID userID, String weekStr, UUID goalID)
	{
		ActivityController methodOn = methodOn(ActivityController.class);
		return linkTo(methodOn.getWeekActivity(null, userID, weekStr, goalID));
	}

	static class WeekActivityResource extends Resource<WeekActivityDTO>
	{
		public WeekActivityResource(WeekActivityDTO weekActivity)
		{
			super(weekActivity);
		}
	}

	static class DayActivityResource extends Resource<DayActivityDTO>
	{
		public DayActivityResource(DayActivityDTO dayActivity)
		{
			super(dayActivity);
		}
	}

	static class WeekActivityResourceAssembler extends ResourceAssemblerSupport<WeekActivityDTO, WeekActivityResource>
	{
		private UUID userID;

		public WeekActivityResourceAssembler(UUID userID)
		{
			super(ActivityController.class, WeekActivityResource.class);
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
			return new WeekActivityResource(weekActivity);
		}

		private void addSelfLink(WeekActivityResource weekActivityResource)
		{
			weekActivityResource.add(getWeekActivityLinkBuilder(userID, weekActivityResource.getContent().getDate(),
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
		private UUID userID;

		public DayActivityResourceAssembler(UUID userID)
		{
			super(ActivityController.class, DayActivityResource.class);
			this.userID = userID;
		}

		@Override
		public DayActivityResource toResource(DayActivityDTO dayActivity)
		{
			DayActivityResource dayActivityResource = instantiateResource(dayActivity);
			addSelfLink(dayActivityResource);
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
			dayActivityResource.add(getDayActivityLinkBuilder(userID, dayActivityResource.getContent().getDate(),
					dayActivityResource.getContent().getGoalID()).withSelfRel());
		}

		private void addGoalLink(DayActivityResource dayActivityResource)
		{
			dayActivityResource
					.add(GoalController.getGoalLinkBuilder(userID, dayActivityResource.getContent().getGoalID()).withRel("goal"));
		}
	}
}
