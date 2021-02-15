/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface BuddyAnonymizedRepository extends CrudRepository<BuddyAnonymized, UUID>
{
	@Query("select count(b) > 0 from BuddyAnonymized b where b.owningUserAnonymized.id = :owningUserAnonymizedId and (userAnonymizedId = :userAnonymizedId or receiving_status = 1)")
	boolean existsPendingOrEstablishedBuddyRelationship(@Param("owningUserAnonymizedId") UUID owningUserAnonymizedId,
			@Param("userAnonymizedId") UUID userAnonymizedId);
}
