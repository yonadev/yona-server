/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;

@Service
public class AnalysisEngineCacheService
{
	@Cacheable(value = "activities", key = "{#userAnonymizedID,#goalID,#forDate}")
	public DayActivity fetchDayActivityForUser(UUID userAnonymizedID, UUID goalID, LocalDate forDate)
	{
		return DayActivity.getRepository().findDayActivity(userAnonymizedID, goalID, forDate);
	}

	@CachePut(value = "activities", key = "{#activity.userAnonymizedID,#dayActivity.goalID,#dayActivity.localDate}")
	public DayActivity updateDayActivityForUser(DayActivity dayActivity)
	{
		return DayActivity.getRepository().save(dayActivity);
	}
}
