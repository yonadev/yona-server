/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;

@Service
public class ActivityCacheService
{
	@Cacheable(value = "dayActivities", key = "{#userAnonymizedID,#goalID}")
	public DayActivity fetchLastDayActivityForUser(UUID userAnonymizedID, UUID goalID)
	{
		return DayActivity.getRepository().findLast(userAnonymizedID, goalID);
	}

	@CachePut(value = "dayActivities", key = "{#dayActivity.userAnonymized.getID(),#dayActivity.goal.getID()}")
	public DayActivity updateLastDayActivityForUser(DayActivity dayActivity)
	{
		// Nothing else to do. Just let Spring cache this new value.
		return dayActivity;
	}

	public WeekActivity fetchWeekActivityForUser(UUID userAnonymizedID, UUID goalID, LocalDate date)
	{
		return WeekActivity.getRepository().findOne(userAnonymizedID, goalID, date);
	}

	public WeekActivity updateWeekActivityForUser(WeekActivity weekActivity)
	{
		return WeekActivity.getRepository().save(weekActivity);
	}
}
