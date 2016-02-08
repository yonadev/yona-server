package nu.yona.server.goals.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.goals.service.BudgetGoalDTO;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/users/{userID}/goals")
public class GoalController
{
	@Autowired
	private UserService userService;

	@Autowired
	private GoalService goalService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<Resource<GoalDTO>>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(userID, goalService.getGoalsOfUser(userID), getAllGoalsLinkBuilder(userID)));
	}

	private static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID userID)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getAllGoals(null, userID));
	}

	@RequestMapping(value = "/budgetGoals/{goalID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resource<GoalDTO>> getBudgetGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable UUID goalID)
	{

		return getGoal(password, userID, goalID);
	}

	private HttpEntity<Resource<GoalDTO>> getGoal(Optional<String> password, UUID userID, UUID goalID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createResponse(userID, goalService.getGoal(userID, goalID), HttpStatus.OK));
	}

	@RequestMapping(value = "/budgetGoals/", method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<Resource<GoalDTO>> addBudgetGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @RequestBody Resource<BudgetGoalDTO> goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		return addGoal(password, userID, goal, messageStr);
	}

	private HttpEntity<Resource<GoalDTO>> addGoal(Optional<String> password, UUID userID, Resource<BudgetGoalDTO> goal,
			String messageStr)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> createResponse(userID,
				goalService.addGoal(userID, goal.getContent(), Optional.ofNullable(messageStr)), HttpStatus.CREATED));
	}

	@RequestMapping(value = "/budgetGoals/{goalID}", method = RequestMethod.DELETE)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void removeBudgetGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@PathVariable UUID goalID, @RequestParam(value = "message", required = false) String messageStr)
	{
		removeGoal(password, userID, goalID, messageStr);
	}

	private void removeGoal(Optional<String> password, UUID userID, UUID goalID, String messageStr)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> {
			goalService.removeGoal(userID, goalID, Optional.ofNullable(messageStr));
			return null;
		});
	}

	private HttpEntity<Resource<GoalDTO>> createResponse(UUID userID, GoalDTO goal, HttpStatus status)
	{
		return new ResponseEntity<Resource<GoalDTO>>(buildResource(goal, userID), status);
	}

	private Resource<GoalDTO> buildResource(GoalDTO goal, UUID userID)
	{
		return new Resource<GoalDTO>(goal, getGoalLinkBuilder(userID, goal).withSelfRel());
	}

	private HttpEntity<Resources<Resource<GoalDTO>>> createOKResponse(UUID userID, Set<GoalDTO> goals,
			ControllerLinkBuilder controllerMethodLinkBuilder)
	{
		return new ResponseEntity<Resources<Resource<GoalDTO>>>(
				new Resources<>(goals.stream().map(goal -> buildResource(goal, userID))::iterator,
						controllerMethodLinkBuilder.withSelfRel()),
				HttpStatus.OK);
	}

	static ControllerLinkBuilder getGoalLinkBuilder(UUID userID, GoalDTO goal)
	{
		GoalController methodOn = methodOn(GoalController.class);
		if (goal instanceof BudgetGoalDTO)
		{
			return linkTo(methodOn.getBudgetGoal(Optional.empty(), userID, goal.getID()));
		}
		else
		{
			throw new NotImplementedException("Goal type '" + goal.getClass() + "' not recognized");
		}
	}

	static class GoalResource extends Resource<GoalDTO>
	{
		public GoalResource(GoalDTO goal)
		{
			super(goal);
		}
	}

	public static class GoalResourceAssembler extends ResourceAssemblerSupport<GoalDTO, GoalResource>
	{
		private UUID userID;

		public GoalResourceAssembler(UUID userID)
		{
			super(GoalController.class, GoalResource.class);
			this.userID = userID;
		}

		@Override
		public GoalResource toResource(GoalDTO goal)
		{
			GoalResource goalResource = instantiateResource(goal);
			ControllerLinkBuilder selfLinkBuilder = getGoalLinkBuilder(userID, goal);
			addSelfLink(selfLinkBuilder, goalResource);
			return goalResource;
		}

		@Override
		protected GoalResource instantiateResource(GoalDTO goal)
		{
			return new GoalResource(goal);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, GoalResource goalResource)
		{
			goalResource.add(selfLinkBuilder.withSelfRel());
		}
	}
}