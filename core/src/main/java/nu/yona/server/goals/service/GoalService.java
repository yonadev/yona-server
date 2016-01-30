package nu.yona.server.goals.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class GoalService
{
	@Autowired
	private UserService userService;

	public Set<GoalDTO> getGoalsOfUser(UUID forUserID)
	{
		UserDTO user = userService.getPrivateUser(forUserID);
		return user.getPrivateData().getGoals();
	}

	public GoalDTO getGoal(UUID userID, UUID goalID)
	{
		UserDTO user = userService.getPrivateUser(userID);
		Optional<GoalDTO> foundGoal = user.getPrivateData().getGoals().stream().filter(goal -> goal.getID().equals(goalID))
				.findFirst();
		if (!foundGoal.isPresent())
		{
			throw GoalServiceException.goalNotFoundById(userID, goalID);
		}
		return foundGoal.get();
	}

	@Transactional
	public GoalDTO addGoal(UUID userID, GoalDTO goal, Optional<String> message)
	{
		GoalDTO addedGoal = userService.addGoal(userID, goal);
		// TODO send message
		return addedGoal;
	}

	@Transactional
	public void removeGoal(UUID userID, UUID goalID, Optional<String> message)
	{
		// TODO check mandatory
		userService.removeGoal(userID, goalID);
		// TODO send message
	}

	@Transactional
	public GoalDTO updateGoal(UUID userID, UUID goalID, GoalDTO newGoal, Optional<String> message)
	{
		GoalDTO currentGoal = getGoal(userID, goalID);
		if (!currentGoal.getActivityCategoryName().equals(newGoal.getActivityCategoryName()))
		{
			throw GoalServiceException.updateGoalCategoryMismatch(userID, goalID, newGoal);
		}

		userService.removeGoal(userID, goalID);
		GoalDTO addedGoal = userService.addGoal(userID, newGoal);
		// TODO send message
		return addedGoal;
	}
}
