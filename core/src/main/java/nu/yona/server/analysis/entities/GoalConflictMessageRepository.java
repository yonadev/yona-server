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
public interface GoalConflictMessageRepository extends CrudRepository<GoalConflictMessage, UUID>
{
	@Query("select m from Message m where TYPE(m) = :type and m.destinationID = :destinationID"
			+ " and m.relatedVPNLoginID = :relatedVPNLoginID and m.goalID = :goalID and m.endTime > :minEndTime order by m.endTime desc")
	List<GoalConflictMessage> findLatestGoalConflictMessageFromDestination(@Param("relatedVPNLoginID") UUID relatedVPNLoginID,
			@Param("goalID") UUID goalID, @Param("destinationID") UUID destinationID, @Param("minEndTime") Date minEndTime,
			@Param("type") Class<GoalConflictMessage> type);
}
