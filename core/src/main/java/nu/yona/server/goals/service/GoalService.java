/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalChangeMessage;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.TimeUtil;

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

	public GoalDTO getGoalForUserID(UUID userID, UUID goalID)
	{
		User userEntity = userService.getUserEntityByID(userID);
		return GoalDTO.createInstance(getGoalEntity(userEntity, goalID));
	}

	public GoalDTO getGoalForUserAnonymizedID(UUID userAnonymizedID, UUID goalID)
	{
		return userAnonymizedService.getUserAnonymized(userAnonymizedID).getGoals().stream().filter(g -> g.getID().equals(goalID))
				.findFirst().orElseThrow(() -> GoalServiceException.goalNotFoundByIdForUserAnonymized(userAnonymizedID, goalID));
	}

	public Goal getGoalEntityForUserAnonymizedID(UUID userAnonymizedID, UUID goalID)
	{
		Goal goal = Goal.getRepository().findOne(goalID);
		if (goal == null)
		{
			throw GoalServiceException.goalNotFoundByIdForUserAnonymized(userAnonymizedID, goalID);
		}
		return goal;
	}

	private Goal getGoalEntity(User userEntity, UUID goalID)
	{
		return getGoalEntity(userEntity.getAnonymized(), goalID);
	}

	private Goal getGoalEntity(UserAnonymized userAnonymized, UUID goalID)
	{
		Optional<Goal> foundGoal = userAnonymized.getGoals().stream().filter(goal -> goal.getID().equals(goalID)).findFirst();
		if (!foundGoal.isPresent())
		{
			throw GoalServiceException.goalNotFoundByIdForUserAnonymized(userAnonymized.getID(), goalID);
		}
		return foundGoal.get();
	}

	@Transactional
	public GoalDTO addGoal(UUID userID, GoalDTO goal, Optional<String> message)
	{
		goal.validate();
		User userEntity = userService.getUserEntityByID(userID);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Optional<Goal> conflictingExistingGoal = userAnonymizedEntity.getGoals().stream()
				.filter(existingGoal -> existingGoal.getActivityCategory().getID().equals(goal.getActivityCategoryID()))
				.findFirst();
		if (conflictingExistingGoal.isPresent())
		{
			throw GoalServiceException.cannotAddSecondGoalOnActivityCategory(conflictingExistingGoal.get().getActivityCategory()
					.getLocalizableName().get(LocaleContextHolder.getLocale()));
		}

		Goal goalEntity = goal.createGoalEntity();
		userAnonymizedEntity.addGoal(goalEntity);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, goalEntity.getActivityCategory(), GoalChangeMessage.Change.GOAL_ADDED, message);

		return GoalDTO.createInstance(goalEntity);
	}

	@Transactional
	public GoalDTO updateGoal(UUID userID, UUID goalID, GoalDTO newGoalDTO, Optional<String> message)
	{
		User userEntity = userService.getUserEntityByID(userID);
		Goal existingGoal = getGoalEntity(userEntity, goalID);

		verifyGoalUpdate(existingGoal, newGoalDTO);
		if (newGoalDTO.getCreationTime().isPresent() && !newGoalDTO.isGoalChanged(existingGoal))
		{
			// Tests update the creation time. Handle that as a special case.
			updateGoalCreationTime(userEntity, existingGoal, newGoalDTO);
		}
		else if (newGoalDTO.isGoalChanged(existingGoal))
		{
			assertNoUpdateToThePast(newGoalDTO, existingGoal);
			updateGoal(userEntity, existingGoal, newGoalDTO, message);
		}

		return GoalDTO.createInstance(existingGoal);
	}

	private void updateGoalCreationTime(User userEntity, Goal existingGoal, GoalDTO newGoalDTO)
	{
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		existingGoal.setCreationTime(newGoalDTO.getCreationTime().get());
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);
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
		cloneExistingGoalAsHistoryItem(userAnonymizedEntity, existingGoal,
				newGoalDTO.getCreationTime().orElse(TimeUtil.utcNow()));
		newGoalDTO.getCreationTime().ifPresent(ct -> existingGoal.setCreationTime(ct));
		newGoalDTO.updateGoalEntity(existingGoal);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, existingGoal.getActivityCategory(), GoalChangeMessage.Change.GOAL_CHANGED,
				message);
	}

	private void cloneExistingGoalAsHistoryItem(UserAnonymized userAnonymizedEntity, Goal existingGoal, LocalDateTime endTime)
	{
		Goal historyGoal = existingGoal.cloneAsHistoryItem(endTime);
		existingGoal.setPreviousVersionOfThisGoal(historyGoal);
		WeekActivity.getRepository().findByGoal(existingGoal).stream()
				.filter(a -> historyGoal.wasActiveAtInterval(a.getStartTime(), ChronoUnit.WEEKS))
				.forEach(a -> setGoalAndSave(a, historyGoal));
		DayActivity.getRepository().findByGoal(existingGoal).stream()
				.filter(a -> historyGoal.wasActiveAtInterval(a.getStartTime(), ChronoUnit.DAYS))
				.forEach(a -> setGoalAndSave(a, historyGoal));
	}

	private void setGoalAndSave(IntervalActivity activity, Goal goal)
	{
		activity.setGoal(goal);
		IntervalActivity.getIntervalActivityRepository().save(activity);
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

	private void assertNoUpdateToThePast(GoalDTO newGoalDTO, Goal existingGoal)
	{
		if (newGoalDTO.getCreationTime().isPresent()
				&& newGoalDTO.getCreationTime().get().isBefore(existingGoal.getCreationTime()))
		{
			throw GoalServiceException.goalUpdateCannotBeMadeOlderThanOriginal(newGoalDTO.getCreationTime().get(),
					existingGoal.getCreationTime());
		}
	}

	@Transactional
	public void removeGoal(UUID userID, UUID goalID, Optional<String> message)
	{
		User userEntity = userService.getUserEntityByID(userID);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Goal goalEntity = userAnonymizedEntity.getGoals().stream().filter(goal -> goal.getID().equals(goalID)).findFirst()
				.orElseThrow(() -> GoalServiceException.goalNotFoundByIdForUser(userID, goalID));
		if (goalEntity.isMandatory())
		{
			throw GoalServiceException.cannotRemoveMandatoryGoal(userID, goalID);
		}

		ActivityCategory activityCategoryOfChangedGoal = goalEntity.getActivityCategory();
		deleteGoalAndRelatedEntities(userAnonymizedEntity, goalEntity);
		userAnonymizedEntity.removeGoal(goalEntity);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getID(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, activityCategoryOfChangedGoal, GoalChangeMessage.Change.GOAL_DELETED, message);
	}

	private void deleteGoalAndRelatedEntities(UserAnonymized userAnonymizedEntity, Goal goalEntity)
	{
		deleteActivitiesForGoal(goalEntity);
		deleteGoalConflictMessagesForGoal(userAnonymizedEntity, goalEntity);
		goalEntity.getPreviousVersionOfThisGoal().ifPresent(pg -> deleteGoalAndRelatedEntities(userAnonymizedEntity, pg));
		Goal.getRepository().delete(goalEntity);
	}

	private void deleteActivitiesForGoal(Goal goalEntity)
	{
		UUID goalID = goalEntity.getID();
		WeekActivity.getRepository().deleteAllForGoal(goalID);
		DayActivity.getRepository().deleteAllForGoal(goalID);
	}

	private void deleteGoalConflictMessagesForGoal(UserAnonymized userAnonymizedEntity, Goal goalEntity)
	{
		userAnonymizedEntity.getAnonymousDestination().removeGoalConflictMessages(goalEntity);
		userAnonymizedEntity.getBuddiesAnonymized()
				.forEach(ba -> ba.getUserAnonymized().getAnonymousDestination().removeGoalConflictMessages(goalEntity));
	}

	private void broadcastGoalChangeMessage(User userEntity, ActivityCategory activityCategoryOfChangedGoal,
			GoalChangeMessage.Change change, Optional<String> message)
	{
		messageService.broadcastMessageToBuddies(UserAnonymizedDTO.createInstance(userEntity.getAnonymized()),
				() -> GoalChangeMessage.createInstance(userEntity.getID(), userEntity.getUserAnonymizedID(),
						userEntity.getNickname(), activityCategoryOfChangedGoal, change, message.orElse(null)));
	}
}
