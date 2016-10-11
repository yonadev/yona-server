/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;

@Service
// DayActivity entities are cached which should not be serialized, hence the local cache
@CacheConfig(cacheManager = "localCache")
public class ActivityCacheService
{
	@Cacheable(value = "dayActivities", key = "{#userAnonymizedID,#goalID}")
	@Transactional
	public DayActivity fetchLastDayActivityForUser(UUID userAnonymizedID, UUID goalID)
	{
		List<DayActivity> lastActivityList = DayActivity.getRepository().findLast(userAnonymizedID, goalID, new PageRequest(0, 1))
				.getContent();
		return lastActivityList.isEmpty() ? null : lastActivityList.get(0);
	}

	@CachePut(value = "dayActivities", key = "{#dayActivity.userAnonymized.getID(),#dayActivity.goal.getID()}")
	public DayActivity updateLastDayActivityForUser(DayActivity dayActivity)
	{
		// Nothing else to do. Just let Spring cache this new value.
		return dayActivity;
	}

	@Transactional
	public WeekActivity fetchWeekActivityForUser(UUID userAnonymizedID, UUID goalID, LocalDate date)
	{
		return WeekActivity.getRepository().findOne(userAnonymizedID, goalID, date);
	}

	@Transactional
	public WeekActivity updateWeekActivityForUser(WeekActivity weekActivity)
	{
		return WeekActivity.getRepository().save(weekActivity);
	}
}
