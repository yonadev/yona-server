/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityRepository extends CrudRepository<Activity, UUID>
{
	@Query("select a from Activity a"
			+ " where a.userAnonymizedID = :userAnonymizedID and a.goalID = :goalID and a.endTime > :minEndTime order by a.endTime desc")
	List<Activity> findLatestActivity(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("goalID") UUID goalID,
			@Param("minEndTime") Date minEndTime);
}
