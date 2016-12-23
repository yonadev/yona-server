/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.util.UUID;

import nu.yona.server.exceptions.YonaException;

public class ActivityServiceException extends YonaException
{
	private static final long serialVersionUID = -1363053667674820274L;

	private ActivityServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static ActivityServiceException activityDateGoalMismatch(UUID userId, LocalDate date, UUID goalId)
	{
		return new ActivityServiceException("error.activity.date.goal.mismatch", userId, date, goalId);
	}

	public static ActivityServiceException buddyDayActivityNotFound(UUID userId, UUID buddyId, LocalDate date, UUID goalId)
	{
		return new ActivityServiceException("error.activity.buddy.day.activity.not.found", userId, buddyId, date, goalId);
	}
}
