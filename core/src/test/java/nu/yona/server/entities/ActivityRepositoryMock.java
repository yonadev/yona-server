/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang.NotImplementedException;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.ActivityRepository;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.device.entities.DeviceAnonymized;

public class ActivityRepositoryMock extends MockCrudRepositoryEntityWithId<Activity> implements ActivityRepository
{

	@Override
	public List<Activity> findOverlappingOfSameApp(DayActivity dayActivity, UUID deviceAnonymizedId, UUID activityCategoryId,
			String app, LocalDateTime startTime, LocalDateTime endTime)
	{
		throw new NotImplementedException();
	}

	@Override
	public Set<Activity> findByDeviceAnonymized(DeviceAnonymized deviceAnonymized)
	{
		return StreamSupport.stream(findAll().spliterator(), false)
				.filter(a -> a.getDeviceAnonymized().map(DeviceAnonymized::getId).orElse(null) == deviceAnonymized.getId())
				.collect(Collectors.toSet());
	}
}