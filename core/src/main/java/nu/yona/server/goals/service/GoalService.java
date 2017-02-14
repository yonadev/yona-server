/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalChangeMessage;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDto;
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

	public Set<GoalDto> getGoalsOfUser(UUID forUserId)
	{
		UserDto user = userService.getPrivateUser(forUserId);
		return user.getPrivateData().getGoals();
	}

	public GoalDto getGoalForUserId(UUID userId, UUID goalId)
	{
		User userEntity = userService.getUserEntityById(userId);
		return GoalDto.createInstance(getGoalEntity(userEntity, goalId));
	}

	public GoalDto getGoalForUserAnonymizedId(UUID userAnonymizedId, UUID goalId)
	{
		return userAnonymizedService.getUserAnonymized(userAnonymizedId).getGoals().stream()
				.filter(g -> g.getGoalId().equals(goalId)).findFirst()
				.orElseThrow(() -> GoalServiceException.goalNotFoundByIdForUserAnonymized(userAnonymizedId, goalId));
	}

	public Goal getGoalEntityForUserAnonymizedId(UUID userAnonymizedId, UUID goalId)
	{
		Goal goal = Goal.getRepository().findOne(goalId);
		if (goal == null)
		{
			throw GoalServiceException.goalNotFoundByIdForUserAnonymized(userAnonymizedId, goalId);
		}
		return goal;
	}

	private Goal getGoalEntity(User userEntity, UUID goalId)
	{
		return getGoalEntity(userEntity.getAnonymized(), goalId);
	}

	private Goal getGoalEntity(UserAnonymized userAnonymized, UUID goalId)
	{
		Optional<Goal> foundGoal = userAnonymized.getGoals().stream().filter(goal -> goal.getId().equals(goalId)).findFirst();
		if (!foundGoal.isPresent())
		{
			throw GoalServiceException.goalNotFoundByIdForUserAnonymized(userAnonymized.getId(), goalId);
		}
		return foundGoal.get();
	}

	@Transactional
	public GoalDto addGoal(UUID userId, GoalDto goal, Optional<String> message)
	{
		goal.validate();
		User userEntity = userService.getUserEntityById(userId);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Optional<Goal> conflictingExistingGoal = userAnonymizedEntity.getGoals().stream()
				.filter(existingGoal -> existingGoal.getActivityCategory().getId().equals(goal.getActivityCategoryId()))
				.findFirst();
		if (conflictingExistingGoal.isPresent())
		{
			throw GoalServiceException.cannotAddSecondGoalOnActivityCategory(conflictingExistingGoal.get().getActivityCategory()
					.getLocalizableName().get(LocaleContextHolder.getLocale()));
		}

		Goal goalEntity = goal.createGoalEntity();
		userAnonymizedEntity.addGoal(goalEntity);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getId(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, goalEntity.getActivityCategory(), GoalChangeMessage.Change.GOAL_ADDED, message);

		return GoalDto.createInstance(goalEntity);
	}

	@Transactional
	public GoalDto updateGoal(UUID userId, UUID goalId, GoalDto newGoalDto, Optional<String> message)
	{
		User userEntity = userService.getUserEntityById(userId);
		Goal existingGoal = getGoalEntity(userEntity, goalId);

		verifyGoalUpdate(existingGoal, newGoalDto);
		if (newGoalDto.getCreationTime().isPresent() && !newGoalDto.isGoalChanged(existingGoal))
		{
			// Tests update the creation time. Handle that as a special case.
			updateGoalCreationTime(userEntity, existingGoal, newGoalDto);
		}
		else if (newGoalDto.isGoalChanged(existingGoal))
		{
			assertNoUpdateToThePast(newGoalDto, existingGoal);
			updateGoal(userEntity, existingGoal, newGoalDto, message);
		}

		return GoalDto.createInstance(existingGoal);
	}

	private void updateGoalCreationTime(User userEntity, Goal existingGoal, GoalDto newGoalDto)
	{
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		existingGoal.setCreationTime(newGoalDto.getCreationTime().get());
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getId(), userAnonymizedEntity);
	}

	private void verifyGoalUpdate(Goal existingGoal, GoalDto newGoalDto)
	{
		newGoalDto.validate();
		GoalDto existingGoalDto = GoalDto.createInstance(existingGoal);
		assertNoTypeChange(newGoalDto, existingGoalDto);
		assertNoActivityCategoryChange(newGoalDto, existingGoalDto);
	}

	private void updateGoal(User userEntity, Goal existingGoal, GoalDto newGoalDto, Optional<String> message)
	{
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		LocalDateTime goalChangeTime = newGoalDto.getCreationTime().orElse(TimeUtil.utcNow());
		cloneExistingGoalAsHistoryItem(userAnonymizedEntity, existingGoal, goalChangeTime);
		existingGoal.setCreationTime(goalChangeTime);
		newGoalDto.updateGoalEntity(existingGoal);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getId(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, existingGoal.getActivityCategory(), GoalChangeMessage.Change.GOAL_CHANGED,
				message);
	}

	private void cloneExistingGoalAsHistoryItem(UserAnonymized userAnonymizedEntity, Goal existingGoal, LocalDateTime endTime)
	{
		Goal historyGoal = existingGoal.cloneAsHistoryItem(endTime);
		existingGoal.transferHistoryActivities(historyGoal);
		existingGoal.setPreviousVersionOfThisGoal(historyGoal);
	}

	private void assertNoActivityCategoryChange(GoalDto newGoalDto, GoalDto existingGoalDto)
	{
		if (!newGoalDto.getActivityCategoryId().equals(existingGoalDto.getActivityCategoryId()))
		{
			throw GoalServiceException.cannotChangeActivityCategoryOfGoal(newGoalDto.getActivityCategoryId(),
					existingGoalDto.getActivityCategoryId());
		}
	}

	private void assertNoTypeChange(GoalDto newGoalDto, GoalDto existingGoalDto)
	{
		if (!newGoalDto.getClass().equals(existingGoalDto.getClass()))
		{
			throw GoalServiceException.cannotChangeTypeOfGoal(existingGoalDto.getClass().getSimpleName(),
					newGoalDto.getClass().getSimpleName());
		}
	}

	private void assertNoUpdateToThePast(GoalDto newGoalDto, Goal existingGoal)
	{
		if (newGoalDto.getCreationTime().isPresent()
				&& newGoalDto.getCreationTime().get().isBefore(existingGoal.getCreationTime()))
		{
			throw GoalServiceException.goalUpdateCannotBeMadeOlderThanOriginal(newGoalDto.getCreationTime().get(),
					existingGoal.getCreationTime());
		}
	}

	@Transactional
	public void removeGoal(UUID userId, UUID goalId, Optional<String> message)
	{
		User userEntity = userService.getUserEntityById(userId);
		UserAnonymized userAnonymizedEntity = userEntity.getAnonymized();
		Goal goalEntity = userAnonymizedEntity.getGoals().stream().filter(goal -> goal.getId().equals(goalId)).findFirst()
				.orElseThrow(() -> GoalServiceException.goalNotFoundByIdForUser(userId, goalId));
		if (goalEntity.isMandatory())
		{
			throw GoalServiceException.cannotRemoveMandatoryGoal(userId, goalId);
		}

		ActivityCategory activityCategoryOfChangedGoal = goalEntity.getActivityCategory();
		userAnonymizedEntity.removeGoal(goalEntity);
		deleteGoalAndRelatedEntities(userAnonymizedEntity, goalEntity);
		userAnonymizedService.updateUserAnonymized(userAnonymizedEntity.getId(), userAnonymizedEntity);

		broadcastGoalChangeMessage(userEntity, activityCategoryOfChangedGoal, GoalChangeMessage.Change.GOAL_DELETED, message);
	}

	private void deleteGoalAndRelatedEntities(UserAnonymized userAnonymizedEntity, Goal goalEntity)
	{
		deleteGoalRelatedMessages(userAnonymizedEntity, goalEntity);
	}

	private void deleteGoalRelatedMessages(UserAnonymized userAnonymizedEntity, Goal goalEntity)
	{
		deleteGoalConflictMessagesForGoal(userAnonymizedEntity, goalEntity);
		goalEntity.getPreviousVersionOfThisGoal().ifPresent(pg -> deleteGoalRelatedMessages(userAnonymizedEntity, pg));
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
		messageService.broadcastMessageToBuddies(UserAnonymizedDto.createInstance(userEntity.getAnonymized()),
				() -> GoalChangeMessage.createInstance(userEntity.getId(), userEntity.getUserAnonymizedId(),
						userEntity.getNickname(), activityCategoryOfChangedGoal, change, message.orElse(null)));
	}
}
