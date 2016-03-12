package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
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

import nu.yona.server.analysis.service.AnalysisEngineService;
import nu.yona.server.analysis.service.IntervalActivityDTO;
import nu.yona.server.analysis.service.IntervalActivityDTO.ActivityTimeInterval;
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

	@RequestMapping(value = "/{iso8601}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<IntervalActivityResource>> getIntervalActivity(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable String iso8601)
	{
		ActivityTimeInterval timeInterval = ActivityTimeInterval.fromIso8601(iso8601);
		return CryptoSession
				.execute(password,
						() -> userService
								.canAccessPrivateData(
										userID),
						() -> new ResponseEntity<Resources<IntervalActivityResource>>(new Resources<>(
								new IntervalActivityResourceAssembler(userID)
										.toResources(analysisEngineService.getIntervalActivity(userID, timeInterval)),
								getIntervalActivityLinkBuilder(userID, timeInterval).withSelfRel()), HttpStatus.OK));
	}

	static ControllerLinkBuilder getIntervalActivityLinkBuilder(UUID userID, ActivityTimeInterval timeInterval)
	{
		ActivityController methodOn = methodOn(ActivityController.class);
		return linkTo(methodOn.getIntervalActivity(null, userID, timeInterval.toIso8601()));
	}

	static class IntervalActivityResource extends Resource<IntervalActivityDTO>
	{
		public IntervalActivityResource(IntervalActivityDTO activity)
		{
			super(activity);
		}
	}

	static class IntervalActivityResourceAssembler extends ResourceAssemblerSupport<IntervalActivityDTO, IntervalActivityResource>
	{
		private UUID userID;

		public IntervalActivityResourceAssembler(UUID userID)
		{
			super(ActivityController.class, IntervalActivityResource.class);
			this.userID = userID;
		}

		@Override
		public IntervalActivityResource toResource(IntervalActivityDTO activity)
		{
			IntervalActivityResource activityResource = instantiateResource(activity);
			addGoalLink(activityResource);
			return activityResource;
		}

		@Override
		protected IntervalActivityResource instantiateResource(IntervalActivityDTO activity)
		{
			return new IntervalActivityResource(activity);
		}

		private void addGoalLink(Resource<IntervalActivityDTO> activityResource)
		{
			activityResource
					.add(GoalController.getGoalLinkBuilder(userID, activityResource.getContent().getGoalID()).withRel("goal"));
		}
	}
}
