/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class ActivityServiceException extends YonaException
{
	private static final long serialVersionUID = -1363053667674820274L;

	private ActivityServiceException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	private ActivityServiceException(HttpStatus statusCode, String messageId, Serializable... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static ActivityServiceException activityDateGoalMismatch(UUID userId, LocalDate date, UUID goalId)
	{
		return new ActivityServiceException("error.activity.date.goal.mismatch", userId, date, goalId);
	}

	public static ActivityServiceException buddyDayActivityNotFound(UUID userId, UUID buddyId, LocalDate date, UUID goalId)
	{
		return new ActivityServiceException("error.activity.buddy.day.activity.not.found", userId, buddyId, date, goalId);
	}

	public static ActivityServiceException lastActivityNotFoundOnDayActivity(UUID deviceAnonymizedId,
			int numActivitiesOnDayActivity)
	{
		return new ActivityServiceException(HttpStatus.INTERNAL_SERVER_ERROR,
				"error.activity.last.activity.not.found.on.day.activity", deviceAnonymizedId, numActivitiesOnDayActivity);
	}
}