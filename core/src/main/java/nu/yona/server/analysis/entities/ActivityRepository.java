/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import nu.yona.server.device.entities.DeviceAnonymized;

@Repository
public interface ActivityRepository extends CrudRepository<Activity, Long>
{
	@Query("select a from Activity a" + " where a.dayActivity = :dayActivity and a.deviceAnonymized.id = :deviceAnonymizedId and"
			+ " a.activityCategory.id = :activityCategoryId and a.app = :app and"
			+ " ((:startTime >= a.startTime and :startTime <= a.endTime) or" // New activity started during existing activity
			+ " (:endTime >= a.startTime and :endTime <= a.endTime) or" // New activity ended during existing activity
			+ " (a.startTime >= :startTime and a.endTime <= :endTime))")
		// Existing activity occurred during new activity
	List<Activity> findOverlappingOfSameApp(@Param("dayActivity") DayActivity dayActivity,
			@Param("deviceAnonymizedId") UUID deviceAnonymizedId, @Param("activityCategoryId") UUID activityCategoryId,
			@Param("app") String app, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

	Set<Activity> findByDeviceAnonymized(DeviceAnonymized deviceAnonymized);

	@Modifying
	@Query("update Activity a set a.deviceAnonymized = null where a.deviceAnonymized.id = :deviceAnonymizedId")
	void disconnectAllActivitiesFromDevice(@Param("deviceAnonymizedId") UUID deviceAnonymizedId);
}
