/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;

@Service
// Day activities are only used in the analysis engine service, so a local cache suffices for as long as we do not
// scale out the analysis engine service.
@CacheConfig(cacheManager = "localCache", cacheNames = "lastActivity")
public class ActivityCacheService
{
	@Cacheable(key = "{#userAnonymizedId,#deviceAnonymizedId,#goalId}")
	@Transactional
	public Optional<ActivityDto> fetchLastActivityForUser(UUID userAnonymizedId, UUID deviceAnonymizedId, UUID goalId)
	{
		List<DayActivity> lastDayActivityList = DayActivity.getRepository()
				.findLast(userAnonymizedId, goalId, PageRequest.of(0, 1)).getContent();
		if (lastDayActivityList.isEmpty())
		{
			return Optional.empty();
		}
		// Return activity DTO, or empty, in case the DayActivity is created with inactivity
		return lastDayActivityList.get(0).getLastActivity(deviceAnonymizedId).map(entity -> ActivityDto.createInstance(entity));
	}

	@CachePut(key = "{#userAnonymizedId,#deviceAnonymizedId,#goalId}")
	public ActivityDto updateLastActivityForUser(UUID userAnonymizedId, UUID deviceAnonymizedId, UUID goalId,
			ActivityDto activity)
	{
		// Nothing else to do. Just let Spring cache this new value.
		return activity;
	}
}
