/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeekActivityRepository extends CrudRepository<WeekActivity, UUID>
{
	@Query("select a from WeekActivity a"
			+ " where a.userAnonymized.id = :userAnonymizedID and a.goal.id = :goalID and a.startTime = :startOfWeek")
	WeekActivity findOne(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("goalID") UUID goalID,
			@Param("startOfWeek") ZonedDateTime startOfWeek);

	@Modifying
	@Query("delete from WeekActivity a where a.userAnonymized.id = :userAnonymizedID")
	void deleteAllForUser(@Param("userAnonymizedID") UUID userAnonymizedID);
}
