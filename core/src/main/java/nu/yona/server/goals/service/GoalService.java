package nu.yona.server.goals.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalChangeMessage;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
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

	@Autowired
	private MessageService messageService;

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
		User userEntity = userService.getUserByID(userID);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Optional<Goal> conflictingExistingGoal = userAnonymizedEntity.getGoals().stream()
				.filter(existingGoal -> existingGoal.getActivityCategory().getName().equals(goal.getActivityCategoryName()))
				.findFirst();
		if (conflictingExistingGoal.isPresent())
		{
			throw GoalServiceException.cannotAddSecondGoalOnActivityCategory(goal.getActivityCategoryName());
		}

		Goal goalEntity = goal.createGoalEntity();
		userAnonymizedEntity.addGoal(goalEntity);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, goalEntity, GoalChangeMessage.Change.GOAL_ADDED, message);

		return GoalDTO.createInstance(goalEntity);
	}

	@Transactional
	public void removeGoal(UUID userID, UUID goalID, Optional<String> message)
	{
		// TODO check mandatory
		User userEntity = userService.getUserByID(userID);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Optional<Goal> goalEntity = userAnonymizedEntity.getGoals().stream().filter(goal -> goal.getID().equals(goalID))
				.findFirst();
		if (!goalEntity.isPresent())
		{
			throw GoalServiceException.goalNotFoundById(userID, goalID);
		}
		userAnonymizedEntity.removeGoal(goalEntity.get());
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, goalEntity.get(), GoalChangeMessage.Change.GOAL_DELETED, message);
	}

	private void broadcastGoalChangeMessage(User userEntity, Goal changedGoal, GoalChangeMessage.Change change,
			Optional<String> message)
	{
		messageService.broadcastMessageToBuddies(UserAnonymizedDTO.createInstance(userEntity.getAnonymized()),
				() -> GoalChangeMessage.createInstance(userEntity.getUserAnonymizedID(), userEntity.getID(),
						userEntity.getNickname(), changedGoal, change, message.orElse(null)));
	}
}
