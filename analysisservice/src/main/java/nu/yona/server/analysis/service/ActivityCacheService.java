/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;

@Service
public class ActivityCacheService
{
	@Cacheable(value = "activities", key = "{#userAnonymizedID,#goalID}")
	@Transactional
	public ActivityDTO fetchLastActivityForUser(UUID userAnonymizedID, UUID goalID)
	{
		List<DayActivity> lastActivityList = DayActivity.getRepository().findLast(userAnonymizedID, goalID, new PageRequest(0, 1))
				.getContent();
		return lastActivityList.isEmpty() ? null : ActivityDTO.createInstance(lastActivityList.get(0).getLastActivity());
	}

	@CachePut(value = "activities", key = "{#userAnonymizedID,#goalID}")
	public ActivityDTO updateLastActivityForUser(UUID userAnonymizedID, UUID goalID, ActivityDTO activity)
	{
		// Nothing else to do. Just let Spring cache this new value.
		return activity;
	}
}
