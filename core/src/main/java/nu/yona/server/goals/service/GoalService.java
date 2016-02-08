package nu.yona.server.goals.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class GoalService
{
	@Autowired
	private UserService userService;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

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
		UserAnonymized userAnonymized = userService.getUserByID(userID).getAnonymized();
		Optional<Goal> conflictingExistingGoal = userAnonymized.getGoals().stream()
				.filter(existingGoal -> existingGoal.getActivityCategory().getName().equals(goal.getActivityCategoryName()))
				.findFirst();
		if (conflictingExistingGoal.isPresent())
		{
			throw GoalServiceException.cannotAddSecondGoalOnActivityCategory(goal.getActivityCategoryName());
		}

		Goal goalEntity = goal.createGoalEntity();
		userAnonymized.addGoal(goalEntity);
		userAnonymizedService.updateUserAnonymized(userAnonymized.getID(), userAnonymized);
		// TODO send message
		return GoalDTO.createInstance(goalEntity);
	}

	@Transactional
	public void removeGoal(UUID userID, UUID goalID, Optional<String> message)
	{
		// TODO check mandatory
		UserAnonymized userAnonymized = userService.getUserByID(userID).getAnonymized();
		Optional<Goal> goalEntity = userAnonymized.getGoals().stream().filter(goal -> goal.getID().equals(goalID)).findFirst();
		if (!goalEntity.isPresent())
		{
			throw GoalServiceException.goalNotFoundById(userID, goalID);
		}
		userAnonymized.removeGoal(goalEntity.get());
		userAnonymizedService.updateUserAnonymized(userAnonymized.getID(), userAnonymized);
		// TODO send message
	}
}
