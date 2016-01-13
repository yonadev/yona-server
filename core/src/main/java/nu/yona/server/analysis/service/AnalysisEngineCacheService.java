/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;

@Service
public class AnalysisEngineCacheService
{
	@Cacheable(value = "activities", key = "{#userAnonymizedID,#goalID}")
	public Activity fetchLatestActivityForUser(UUID userAnonymizedID, UUID goalID, Date minEndTime)
	{
		List<Activity> results = Activity.getRepository().findLatestActivity(userAnonymizedID, goalID, minEndTime);

		return results != null && !results.isEmpty() ? results.get(0) : null;
	}

	@CachePut(value = "activities", key = "{#activity.userAnonymizedID,#activity.goalID}")
	public Activity updateLatestActivityForUser(Activity activity)
	{
		return Activity.getRepository().save(activity);
	}
}
