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
public interface WeekActivityRepository extends CrudRepository<WeekActivity, Long>
{
	@Query("select a from WeekActivity a"
			+ " where a.userAnonymized.id = :userAnonymizedId and a.goal.id = :goalId and a.startDate = :startDate")
	WeekActivity findOne(@Param("userAnonymizedId") UUID userAnonymizedId, @Param("goalId") UUID goalId,
			@Param("startDate") LocalDate startDate);

	@Query("select a from WeekActivity a"
			+ " where a.userAnonymized.id = :userAnonymizedId and a.startDate = :startDate and a.goal.id = :goalId")
	WeekActivity findOne(@Param("userAnonymizedId") UUID userAnonymizedId, @Param("startDate") LocalDate startDate,
			@Param("goalId") UUID goalId);

	@Query("select a from WeekActivity a where a.userAnonymized.id = :userAnonymizedId and a.startDate >= :dateFrom and a.startDate <= :dateUntil order by a.startDate desc")
	Set<WeekActivity> findAll(@Param("userAnonymizedId") UUID userAnonymizedId, @Param("dateFrom") LocalDate dateFrom,
			@Param("dateUntil") LocalDate dateUntil);

	@Modifying
	@Query("delete from WeekActivity a where a.userAnonymized.id = :userAnonymizedId")
	void deleteAllForUser(@Param("userAnonymizedId") UUID userAnonymizedId);

	@Modifying
	@Query("delete from WeekActivity a where a.goal.id = :goalId")
	void deleteAllForGoal(@Param("goalId") UUID goalId);

	Set<WeekActivity> findByGoal(Goal goal);
}
