/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "BUDDY_DEVICES")
public class BuddyDevice extends DeviceBase
{
	// Default constructor is required for JPA
	public BuddyDevice()
	{
	}

	public BuddyDevice(UUID id, String name)
	{
		super(id, name);
	}

	public static BuddyDeviceRepository getRepository()
	{
		return (BuddyDeviceRepository) RepositoryProvider.getRepository(BuddyDevice.class, UUID.class);
	}
}