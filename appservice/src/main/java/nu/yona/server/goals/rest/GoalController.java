package nu.yona.server.goals.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Optional;
import java.util.Set;
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
import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/users/{userID}/goals/")
public class GoalController
{
	@Autowired
	private UserService userService;

	@Autowired
	private GoalService goalService;

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<GoalResource>> getAllGoals(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(userID, goalService.getGoalsOfUser(userID), getAllGoalsLinkBuilder(userID)));
	}

	private Object createOKResponse(UUID userID, Set<GoalDTO> goalsOfUser, Object allGoalsLinkBuilder)
	{
		// TODO Auto-generated method stub
		return null;
	}

	private static ControllerLinkBuilder getAllGoalsLinkBuilder(UUID userID)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getAllGoals(null, userID));
	}

	@RequestMapping(value = "{goalID}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalResource> getGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable UUID goalID)
	{

		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID),
				() -> createOKResponse(userID, goalService.getGoal(userID, goalID)));
	}

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<GoalResource> addGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @RequestBody GoalDTO goal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> createResponse(userID,
				goalService.addGoal(userID, goal, Optional.ofNullable(messageStr)), HttpStatus.CREATED));
	}

	@RequestMapping(value = "{goalID}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<GoalResource> updateGoal(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userID, @PathVariable UUID goalID, @RequestBody GoalDTO updatedGoal,
			@RequestParam(value = "message", required = false) String messageStr)
	{
		return CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> createOKResponse(userID,
				goalService.updateGoal(userID, goalID, updatedGoal, Optional.ofNullable(messageStr))));
	}

	@RequestMapping(value = "{goalID}", method = RequestMethod.DELETE)
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

	private HttpEntity<GoalResource> createOKResponse(UUID userID, GoalDTO goal)
	{
		return createResponse(userID, goal, HttpStatus.OK);
	}

	private HttpEntity<GoalResource> createResponse(UUID userID, GoalDTO goal, HttpStatus status)
	{
		return new ResponseEntity<GoalResource>(new GoalResourceAssembler(userID).toResource(goal), status);
	}

	private HttpEntity<Resources<GoalResource>> createOKResponse(UUID userID, Set<GoalDTO> buddies,
			ControllerLinkBuilder controllerMethodLinkBuilder)
	{
		return new ResponseEntity<Resources<GoalResource>>(new Resources<>(new GoalResourceAssembler(userID).toResources(buddies),
				controllerMethodLinkBuilder.withSelfRel()), HttpStatus.OK);
	}

	static ControllerLinkBuilder getGoalLinkBuilder(UUID userID, UUID goalID)
	{
		GoalController methodOn = methodOn(GoalController.class);
		return linkTo(methodOn.getGoal(Optional.empty(), userID, goalID));
	}

	static class GoalResource extends Resource<GoalDTO>
	{
		public GoalResource(GoalDTO goal)
		{
			super(goal);
		}
	}

	static class GoalResourceAssembler extends ResourceAssemblerSupport<GoalDTO, GoalResource>
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
			ControllerLinkBuilder selfLinkBuilder = getSelfLinkBuilder(goal.getID());
			addSelfLink(selfLinkBuilder, goalResource);
			return goalResource;
		}

		@Override
		protected GoalResource instantiateResource(GoalDTO goal)
		{
			return new GoalResource(goal);
		}

		private ControllerLinkBuilder getSelfLinkBuilder(UUID goalID)
		{
			return getGoalLinkBuilder(userID, goalID);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, GoalResource goalResource)
		{
			goalResource.add(selfLinkBuilder.withSelfRel());
		}
	}

}