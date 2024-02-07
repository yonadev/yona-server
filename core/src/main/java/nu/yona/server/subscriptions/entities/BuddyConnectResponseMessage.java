/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.TinyIntJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

@Entity
public class BuddyConnectResponseMessage extends BuddyConnectMessage
{
	private BuddyAnonymized.Status status = BuddyAnonymized.Status.NOT_REQUESTED;
	@JdbcType(value = TinyIntJdbcType.class)
	@Column(columnDefinition = "bit default false")
	private boolean isProcessed;

	// Default constructor is required for JPA
	public BuddyConnectResponseMessage()
	{
		super();
	}

	private BuddyConnectResponseMessage(BuddyInfoParameters buddyInfoParameters, String message, UUID buddyId,
			Set<UserDevice> devices, BuddyAnonymized.Status status)
	{
		super(buddyInfoParameters, message, buddyId, devices);
		this.status = status;
	}

	public static BuddyConnectResponseMessage createInstance(BuddyInfoParameters buddyInfoParameters, String message,
			UUID buddyId, Set<UserDevice> devices, BuddyAnonymized.Status status)
	{
		return new BuddyConnectResponseMessage(buddyInfoParameters, message, buddyId, devices, status);
	}

	@Override
	public boolean isUserFetchable()
	{
		return status == Status.ACCEPTED;
	}

	public BuddyAnonymized.Status getStatus()
	{
		return status;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	public void setProcessed()
	{
		this.isProcessed = true;
	}
}
