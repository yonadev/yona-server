/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;

@Service
public class ActivityCacheService
{
	private static final Logger logger = LoggerFactory.getLogger(ActivityCacheService.class);

	@Cacheable(value = "dayActivities", key = "{#userAnonymizedID,#goalID}")
	@Transactional
	public DayActivity fetchLastDayActivityForUser(UUID userAnonymizedID, UUID goalID)
	{
		List<DayActivity> lastActivityList = DayActivity.getRepository().findLast(userAnonymizedID, goalID, new PageRequest(0, 1))
				.getContent();
		DayActivity dayActivity = lastActivityList.isEmpty() ? null : lastActivityList.get(0);
		logger.info(
				"YD-295 - ActivityCacheService.fetchLastDayActivityForUser(" + userAnonymizedID + ", " + goalID + ") returning "
						+ ((dayActivity == null) ? "null"
								: ("ID: " + dayActivity.getID() + ", startTime: " + dayActivity.getStartTime())),
				new Throwable().fillInStackTrace());
		return dayActivity;
	}

	@CachePut(value = "dayActivities", key = "{#dayActivity.userAnonymized.getID(),#dayActivity.goal.getID()}")
	public DayActivity updateLastDayActivityForUser(DayActivity dayActivity)
	{
		logger.info("YD-295 - ActivityCacheService.updateLastDayActivityForUser(ID: " + dayActivity.getID() + ", startTime: "
				+ dayActivity.getStartTime(), new Throwable().fillInStackTrace());
		// Nothing else to do. Just let Spring cache this new value.
		return dayActivity;
	}

	@Transactional
	public WeekActivity fetchWeekActivityForUser(UUID userAnonymizedID, UUID goalID, LocalDate date)
	{
		WeekActivity weekActivity = WeekActivity.getRepository().findOne(userAnonymizedID, goalID, date);
		logger.info(
				"YD-295 - ActivityCacheService.fetchWeekActivityForUser(" + userAnonymizedID + ", " + goalID + ", " + date
						+ ") returning "
						+ ((weekActivity == null) ? "null"
								: ("ID: " + weekActivity.getID() + ", startTime: " + weekActivity.getStartTime())),
				new Throwable().fillInStackTrace());
		return weekActivity;
	}

	@Transactional
	public WeekActivity updateWeekActivityForUser(WeekActivity weekActivity)
	{
		logger.info("YD-295 - ActivityCacheService.updateWeekActivityForUser(ID: " + weekActivity.getID() + ", startTime: "
				+ weekActivity.getStartTime(), new Throwable().fillInStackTrace());
		return WeekActivity.getRepository().save(weekActivity);
	}
}
