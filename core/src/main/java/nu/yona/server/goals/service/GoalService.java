/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;
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
		User userEntity = userService.getUserByID(userID);
		return GoalDTO.createInstance(getGoalEntity(userEntity, goalID));
	}

	private Goal getGoalEntity(User userEntity, UUID goalID)
	{
		Optional<Goal> foundGoal = userEntity.getGoals().stream().filter(goal -> goal.getID().equals(goalID)).findFirst();
		if (!foundGoal.isPresent())
		{
			throw GoalServiceException.goalNotFoundById(userEntity.getID(), goalID);
		}
		return foundGoal.get();
	}

	@Transactional
	public GoalDTO addGoal(UUID userID, GoalDTO goal, Optional<String> message)
	{
		goal.validate();
		User userEntity = userService.getUserByID(userID);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Optional<Goal> conflictingExistingGoal = userAnonymizedEntity.getGoals().stream()
				.filter(existingGoal -> existingGoal.getActivityCategory().getID().equals(goal.getActivityCategoryID()))
				.findFirst();
		if (conflictingExistingGoal.isPresent())
		{
			throw GoalServiceException.cannotAddSecondGoalOnActivityCategory(
					conflictingExistingGoal.get().getActivityCategory().getName().get(LocaleContextHolder.getLocale()));
		}

		Goal goalEntity = goal.createGoalEntity();
		userAnonymizedEntity.addGoal(goalEntity);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, goalEntity, GoalChangeMessage.Change.GOAL_ADDED, message);

		return GoalDTO.createInstance(goalEntity);
	}

	@Transactional
	public GoalDTO updateGoal(UUID userID, UUID goalID, GoalDTO newGoalDTO, Optional<String> message)
	{
		User userEntity = userService.getUserByID(userID);
		Goal existingGoal = getGoalEntity(userEntity, goalID);

		verifyGoalUpdate(existingGoal, newGoalDTO);

		if (newGoalDTO.isGoalChanged(existingGoal))
		{
			updateGoal(userEntity, existingGoal, newGoalDTO, message);
		}

		return GoalDTO.createInstance(existingGoal);
	}

	private void verifyGoalUpdate(Goal existingGoal, GoalDTO newGoalDTO)
	{
		newGoalDTO.validate();
		GoalDTO existingGoalDTO = GoalDTO.createInstance(existingGoal);
		assertNoTypeChange(newGoalDTO, existingGoalDTO);
		assertNoActivityCategoryChange(newGoalDTO, existingGoalDTO);
	}

	private void updateGoal(User userEntity, Goal existingGoal, GoalDTO newGoalDTO, Optional<String> message)
	{
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		cloneExistingGoalAsHistoryItem(userAnonymizedEntity, existingGoal);
		newGoalDTO.updateGoalEntity(existingGoal);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, existingGoal, GoalChangeMessage.Change.GOAL_CHANGED, message);
	}

	private void cloneExistingGoalAsHistoryItem(UserAnonymized userAnonymizedEntity, Goal existingGoal)
	{
		Goal historyGoal = existingGoal.cloneAsHistoryItem();
		existingGoal.setPreviousGoal(historyGoal);
		WeekActivity.getRepository().findByGoal(existingGoal).stream().forEach(a -> a.setGoal(historyGoal));
		DayActivity.getRepository().findByGoal(existingGoal).stream().forEach(a -> a.setGoal(historyGoal));
	}

	private void assertNoActivityCategoryChange(GoalDTO newGoalDTO, GoalDTO existingGoalDTO)
	{
		if (!newGoalDTO.getActivityCategoryID().equals(existingGoalDTO.getActivityCategoryID()))
		{
			throw GoalServiceException.cannotChangeActivityCategoryOfGoal(newGoalDTO.getActivityCategoryID(),
					existingGoalDTO.getActivityCategoryID());
		}
	}

	private void assertNoTypeChange(GoalDTO newGoalDTO, GoalDTO existingGoalDTO)
	{
		if (!newGoalDTO.getClass().equals(existingGoalDTO.getClass()))
		{
			throw GoalServiceException.cannotChangeTypeOfGoal(existingGoalDTO.getClass().getSimpleName(),
					newGoalDTO.getClass().getSimpleName());
		}
	}

	@Transactional
	public void removeGoal(UUID userID, UUID goalID, Optional<String> message)
	{
		User userEntity = userService.getUserByID(userID);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Optional<Goal> goalEntity = userAnonymizedEntity.getGoals().stream().filter(goal -> goal.getID().equals(goalID))
				.findFirst();
		if (!goalEntity.isPresent())
		{
			throw GoalServiceException.goalNotFoundById(userID, goalID);
		}
		if (goalEntity.get().isMandatory())
		{
			throw GoalServiceException.cannotRemoveMandatoryGoal(userID, goalID);
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
