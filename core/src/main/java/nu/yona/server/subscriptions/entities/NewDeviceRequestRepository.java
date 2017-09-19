/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface NewDeviceRequestRepository extends CrudRepository<NewDeviceRequest, UUID>
{
	@Modifying
	@Query("delete from NewDeviceRequest n where n.creationTime < :cuttOffDate")
	void deleteAllOlderThan(@Param("cuttOffDate") LocalDateTime cuttOffDate);
}
