/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.Buddy;

@Entity
@Table(name = "BUDDY_DEVICES")
public class BuddyDevice extends DeviceBase
{
	/**
	 * The buddy owning this buddy device entity.
	 */
	@ManyToOne
	private Buddy owningBuddy;

	// Default constructor is required for JPA
	public BuddyDevice()
	{
	}

	private BuddyDevice(UUID id, String name, UUID deviceAnonymizedId)
	{
		super(id, name, deviceAnonymizedId);
	}

	public static BuddyDevice createInstance(String name, UUID deviceAnonymizedId)
	{
		return new BuddyDevice(UUID.randomUUID(), name, deviceAnonymizedId);
	}

	public static BuddyDeviceRepository getRepository()
	{
		return (BuddyDeviceRepository) RepositoryProvider.getRepository(BuddyDevice.class, UUID.class);
	}

	public void setOwningBuddy(Buddy buddy)
	{
		owningBuddy = buddy;
	}

	public void clearOwningBuddy()
	{
		owningBuddy = null;
	}
}