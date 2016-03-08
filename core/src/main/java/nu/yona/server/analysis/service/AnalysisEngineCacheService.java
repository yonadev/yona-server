/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.subscriptions.service.UserAnonymizedService;

@Service
public class AnalysisEngineCacheService
{
	@Autowired
	private UserAnonymizedService userAnonymizedService;

	@Cacheable(value = "dayActivities", key = "{#userAnonymizedID,#goalID,#startOfDay}")
	public DayActivity fetchDayActivityForUser(UUID userAnonymizedID, UUID goalID, ZonedDateTime startOfDay)
	{
		return DayActivity.getRepository().findOne(userAnonymizedID, goalID, startOfDay);
	}

	@CachePut(value = "dayActivities", key = "{#dayActivity.userAnonymized.getID(),#dayActivity.goal.getID(),#dayActivity.startTime}")
	public DayActivity updateDayActivityForUser(DayActivity dayActivity)
	{
		return DayActivity.getRepository().save(dayActivity);
	}

	public WeekActivity fetchWeekActivityForUser(UUID userAnonymizedID, UUID goalID, ZonedDateTime startOfWeek)
	{
		return WeekActivity.getRepository().findOne(userAnonymizedID, goalID, startOfWeek);
	}

	public void updateWeekActivityForUser(WeekActivity weekActivity)
	{
		// update via parent
		userAnonymizedService.updateUserAnonymized(weekActivity.getUserAnonymized().getID(), weekActivity.getUserAnonymized());
	}
}
