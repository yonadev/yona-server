/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import nu.yona.server.analysis.entities.IntervalActivity;

@Repository
public interface MessageRepository extends CrudRepository<Message, UUID>
{
	@Query("select m from Message m, MessageDestination d where d.id = :destinationId and m member of d.messages order by m.creationTime desc")
	Page<Message> findFromDestination(@Param("destinationId") UUID destinationId, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationId and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findReceivedMessagesFromDestination(@Param("destinationId") UUID destinationId, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationId and m.isRead = false and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findUnreadReceivedMessagesFromDestination(@Param("destinationId") UUID destinationId, Pageable pageable);

	@Query("select m from Message m, MessageDestination d, Message threadHeadMessage"
			+ " where d.id = :destinationId and m member of d.messages and m.intervalActivity = :intervalActivity and threadHeadMessage.id = m.threadHeadMessageId"
			+ " order by threadHeadMessage.creationTime asc, m.creationTime asc")
	Page<Message> findByIntervalActivity(@Param("destinationId") UUID destinationId,
			@Param("intervalActivity") IntervalActivity intervalActivityEntity, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationId and m.creationTime >= :earliestDateTime and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findReceivedMessagesFromDestinationSinceDate(@Param("destinationId") UUID destinationId,
			@Param("earliestDateTime") LocalDateTime earliestDateTime, Pageable pageable);

	@Modifying
	@Query("delete from Message m where m.intervalActivity in :intervalActivities")
	void deleteMessagesForIntervalActivities(@Param("intervalActivities") Collection<IntervalActivity> intervalActivities);
}
