/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;

@Service
public class AnalysisEngineCacheService
{
	@Cacheable(value = "dayActivities", key = "{#userAnonymizedID,#goalID,#zonedStartOfDay}")
	public DayActivity fetchDayActivityForUser(UUID userAnonymizedID, UUID goalID, ZonedDateTime zonedStartOfDay)
	{
		return DayActivity.getRepository().findOne(userAnonymizedID, goalID, zonedStartOfDay);
	}

	@CachePut(value = "dayActivities", key = "{#dayActivity.userAnonymizedID,#dayActivity.goalID,#dayActivity.zonedStartTime}")
	public DayActivity updateDayActivityForUser(DayActivity dayActivity)
	{
		return DayActivity.getRepository().save(dayActivity);
	}

	public WeekActivity fetchWeekActivityForUser(UUID userAnonymizedID, UUID goalID, ZonedDateTime zonedStartOfWeek)
	{
		return WeekActivity.getRepository().findOne(userAnonymizedID, goalID, zonedStartOfWeek);
	}

	public WeekActivity updateWeekActivityForUser(WeekActivity weekActivity)
	{
		return WeekActivity.getRepository().save(weekActivity);
	}
}
