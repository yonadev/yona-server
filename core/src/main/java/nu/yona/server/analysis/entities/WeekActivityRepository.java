/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import nu.yona.server.goals.entities.Goal;

@Repository
public interface WeekActivityRepository extends CrudRepository<WeekActivity, UUID>
{
	@Query("select a from WeekActivity a"
			+ " where a.userAnonymized.id = :userAnonymizedID and a.goal.id = :goalID and a.date = :date")
	WeekActivity findOne(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("goalID") UUID goalID,
			@Param("date") LocalDate date);

	@Query("select a from WeekActivity a"
			+ " where a.userAnonymized.id = :userAnonymizedID and a.date = :date and a.goal.id = :goalID")
	WeekActivity findOne(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("date") LocalDate date,
			@Param("goalID") UUID goalID);

	@Query("select a from WeekActivity a where a.userAnonymized.id = :userAnonymizedID and a.date >= :dateFrom and a.date <= :dateUntil order by a.startTime desc")
	Set<WeekActivity> findAll(@Param("userAnonymizedID") UUID userAnonymizedID, @Param("dateFrom") LocalDate dateFrom,
			@Param("dateUntil") LocalDate dateUntil);

	@Modifying
	@Query("delete from WeekActivity a where a.userAnonymized.id = :userAnonymizedID")
	void deleteAllForUser(@Param("userAnonymizedID") UUID userAnonymizedID);

	Set<WeekActivity> findByGoal(Goal goal);
}
