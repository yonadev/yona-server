/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BuddyDeviceRepository extends JpaRepository<BuddyDevice, UUID>
{
}
