/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
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

	public static GoalServiceException goalNotFoundById(UUID userID, UUID goalID)
	{
		return new GoalServiceException("error.goal.not.found.id", userID, goalID);
	}

	public static GoalServiceException cannotAddSecondGoalOnActivityCategory(String activityCategoryName)
	{
		return new GoalServiceException("error.goal.cannot.add.second.on.activity.category", activityCategoryName);
	}

	public static GoalServiceException cannotRemoveMandatoryGoal(UUID userID, UUID goalID)
	{
		return new GoalServiceException("error.goal.cannot.remove.mandatory", userID, goalID);
	}
}
