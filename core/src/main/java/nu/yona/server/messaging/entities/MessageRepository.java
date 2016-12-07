/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends CrudRepository<Message, Long>
{
	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m member of d.messages order by m.creationTime desc")
	Page<Message> findFromDestination(@Param("destinationID") UUID destinationID, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findReceivedMessagesFromDestination(@Param("destinationID") UUID destinationID, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m.isRead = false and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findUnreadReceivedMessagesFromDestination(@Param("destinationID") UUID destinationID, Pageable pageable);

	@Query("select m from Message m, MessageDestination d, Message threadHeadMessage"
			+ " where d.id = :destinationID and m member of d.messages and m.activityID = :activityID and threadHeadMessage.id = m.threadHeadMessageID"
			+ " order by threadHeadMessage.creationTime asc, m.creationTime asc")
	Page<Message> findByActivityID(@Param("destinationID") UUID destinationID, @Param("activityID") long activityID,
			Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m.creationTime >= :earliestDateTime and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findReceivedMessagesFromDestinationSinceDate(@Param("destinationID") UUID destinationID,
			@Param("earliestDateTime") LocalDateTime earliestDateTime, Pageable pageable);
}
