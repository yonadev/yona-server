/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends CrudRepository<Message, UUID>
{
	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m member of d.messages order by m.creationTime desc")
	Page<Message> findFromDestination(@Param("destinationID") UUID destinationID, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findReceivedMessagesFromDestination(@Param("destinationID") UUID destinationID, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m.isRead = false and m.isSentItem = false and m member of d.messages order by m.creationTime desc")
	Page<Message> findUnreadReceivedMessagesFromDestination(@Param("destinationID") UUID destinationID, Pageable pageable);

	@Query("select m from Message m, MessageDestination d where d.id = :destinationID and m member of d.messages and m.activityID = :activityID order by m.creationTime asc")
	Page<Message> findByActivityID(@Param("destinationID") UUID destinationID, @Param("activityID") UUID activityID,
			Pageable pageable);
}
