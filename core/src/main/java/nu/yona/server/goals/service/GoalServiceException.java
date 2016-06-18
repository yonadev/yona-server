/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.UUID;

import nu.yona.server.exceptions.YonaException;

public class GoalServiceException extends YonaException
{
	private static final long serialVersionUID = -3190676140223118243L;

	private GoalServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static GoalServiceException goalNotFoundByIdForUserAnonymized(UUID userAnonymizedID, UUID goalID)
	{
		return new GoalServiceException("error.goal.not.found.id.for.user.anonymized", userAnonymizedID, goalID);
	}

	public static GoalServiceException goalNotFoundByIdForUser(UUID userID, UUID goalID)
	{
		return new GoalServiceException("error.goal.not.found.id.for.user", userID, goalID);
	}

	public static GoalServiceException goalNotFoundByIdForBuddy(UUID userID, UUID buddyID, UUID goalID)
	{
		return new GoalServiceException("error.goal.not.found.id.for.buddy", userID, buddyID, goalID);
	}

	public static GoalServiceException cannotAddSecondGoalOnActivityCategory(String activityCategoryName)
	{
		return new GoalServiceException("error.goal.cannot.add.second.on.activity.category", activityCategoryName);
	}

	public static GoalServiceException cannotRemoveMandatoryGoal(UUID userID, UUID goalID)
	{
		return new GoalServiceException("error.goal.cannot.remove.mandatory", userID, goalID);
	}

	public static GoalServiceException cannotChangeTypeOfGoal(String existingClassName, String newClassName)
	{
		return new GoalServiceException("error.goal.cannot.change.type", existingClassName, newClassName);
	}

	public static GoalServiceException cannotChangeActivityCategoryOfGoal(UUID existingCategoryID, UUID newCategoryID)
	{
		return new GoalServiceException("error.goal.cannot.change.activity.category", existingCategoryID, newCategoryID);
	}

	public static GoalServiceException budgetGoalMaxDurationNegative(int maxDurationMinutes)
	{
		return new GoalServiceException("error.goal.budget.invalid.max.duration.cannot.be.negative", maxDurationMinutes);
	}

	public static GoalServiceException timeZoneGoalAtLeastOneZoneRequired()
	{
		return new GoalServiceException("error.goal.time.zone.invalid.at.least.one.zone.required");
	}

	public static GoalServiceException timeZoneGoalInvalidZoneFormat(String zone)
	{
		return new GoalServiceException("error.goal.time.zone.invalid.zone.format", zone);
	}

	public static GoalServiceException timeZoneGoalToNotBeyondFrom(String zone)
	{
		return new GoalServiceException("error.goal.time.zone.to.not.beyond.from", zone);
	}

	public static GoalServiceException timeZoneGoalInvalidHour(String zone, int hour)
	{
		return new GoalServiceException("error.goal.time.zone.not.a.valid.hour", zone, hour);
	}

	public static GoalServiceException timeZoneGoalNotQuarterHour(String zone, int minute)
	{
		return new GoalServiceException("error.goal.time.zone.not.a.quarter.hour", zone, minute);
	}

	public static GoalServiceException timeZoneGoalBeyondTwentyFour(String zone, int minute)
	{
		return new GoalServiceException("error.goal.time.zone.beyond.twenty.four", zone, minute);
	}
}
