/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;

@Service
// Day activities are only used in the analysis engine service, so a local cache suffices for as long as we do not
// scale out the analysis engine service.
@CacheConfig(cacheManager = "localCache", cacheNames = "lastActivity")
public class ActivityCacheService
{
	@Cacheable(key = "{#userAnonymizedID,#goalID}")
	@Transactional
	public ActivityDTO fetchLastActivityForUser(UUID userAnonymizedID, UUID goalID)
	{
		List<DayActivity> lastActivityList = DayActivity.getRepository().findLast(userAnonymizedID, goalID, new PageRequest(0, 1))
				.getContent();
		if (lastActivityList.isEmpty())
		{
			return null;
		}
		Activity lastActivity = lastActivityList.get(0).getLastActivity();
		// this can be the case when a DayActivity is created with inactivity
		return lastActivity == null ? null : ActivityDTO.createInstance(lastActivity);
	}

	@CachePut(key = "{#userAnonymizedID,#goalID}")
	public ActivityDTO updateLastActivityForUser(UUID userAnonymizedID, UUID goalID, ActivityDTO activity)
	{
		// Nothing else to do. Just let Spring cache this new value.
		return activity;
	}
}
