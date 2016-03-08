package nu.yona.server.goals.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(GoalDTO.class)
@RequestMapping(value = "/users/{userID}/goals")
public class GoalController
{
	@Autowired
	private UserService userService;

	@Autowired
	private GoalService goalService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<GoalDTO>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> new ResponseEntity<Resources<GoalDTO>>(
						createAllGoalsCollectionResource(userID, goalService.getGoalsOfUser(userID)), HttpStatus.OK));
	}

	@RequestMapping(value = "/{goalID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalDTO> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable UUID goalID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createResponse(userID, goalService.getGoal(userID, goalID), HttpStatus.OK));
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<GoalDTO> addGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @RequestBody GoalDTO goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> createResponse(userID,
				goalService.addGoal(userID, goal, Optional.ofNullable(messageStr)), HttpStatus.CREATED));
	}

	@RequestMapping(value = "/{goalID}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void removeGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID goalID, @RequestParam(value = "message", required = false) String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> {
			goalService.removeGoal(userID, goalID, Optional.ofNullable(messageStr));
			return null;
		});
	}

	public static Resources<GoalDTO> createAllGoalsCollectionResource(UUID userID, Set<GoalDTO> allGoalsOfUser)
	{
		return new Resources<>(new GoalResourceAssembler(userID).toResources(allGoalsOfUser),
				getAllGoalsLinkBuilder(userID).withSelfRel());
	}

	private static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID userID)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getAllGoals(null, userID));
	}

	private HttpEntity<GoalDTO> createResponse(UUID userID, GoalDTO goal, HttpStatus status)
	{
		return new ResponseEntity<GoalDTO>(new GoalResourceAssembler(userID).toResource(goal), status);
	}

	static ControllerLinkBuilder getGoalLinkBuilder(UUID userID, GoalDTO goal)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getGoal(Optional.empty(), userID, goal.getID()));
	}

	public static class GoalResourceAssembler extends ResourceAssemblerSupport<GoalDTO, GoalDTO>
	{
		private UUID userID;

		public GoalResourceAssembler(UUID userID)
		{
			super(GoalController.class, GoalDTO.class);
			this.userID = userID;
		}

		@Override
		public GoalDTO toResource(GoalDTO goal)
		{
			goal.removeLinks();
			ControllerLinkBuilder selfLinkBuilder = getGoalLinkBuilder(userID, goal);
			addSelfLink(selfLinkBuilder, goal);
			if (!goal.isMandatory())
			{
				addEditLink(selfLinkBuilder, goal);
			}
			return goal;
		}

		@Override
		protected GoalDTO instantiateResource(GoalDTO goal)
		{
			return goal;
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, GoalDTO goalResource)
		{
			goalResource.add(selfLinkBuilder.withSelfRel());
		}

		private void addEditLink(ControllerLinkBuilder selfLinkBuilder, GoalDTO goalResource)
		{
			goalResource.add(selfLinkBuilder.withRel(JsonRootRelProvider.EDIT_REL));
		}
	}
}