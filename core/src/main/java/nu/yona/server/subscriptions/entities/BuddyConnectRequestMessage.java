/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Set;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;

import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.service.BuddyServiceException;

@Entity
public class BuddyConnectRequestMessage extends BuddyConnectMessage
{
	@Column(columnDefinition = "bit default false")
	private boolean isRequestingSending;
	@Column(columnDefinition = "bit default false")
	private boolean isRequestingReceiving;

	private Status status;

	// Default constructor is required for JPA
	public BuddyConnectRequestMessage()
	{
		super();
	}

	private BuddyConnectRequestMessage(BuddyInfoParameters buddyInfoParameters, String message, UUID buddyId,
			Set<UserDevice> devices, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		super(buddyInfoParameters, message, buddyId, devices);

		if (buddyInfoParameters.userId == null)
		{
			throw BuddyServiceException.requestingUserCannotBeNull();
		}

		this.status = Status.REQUESTED;
		this.isRequestingSending = isRequestingSending;
		this.isRequestingReceiving = isRequestingReceiving;
	}

	public static BuddyConnectRequestMessage createInstance(BuddyInfoParameters buddyInfoParameters, String message, UUID buddyId,
			Set<UserDevice> devices, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		return new BuddyConnectRequestMessage(buddyInfoParameters, message, buddyId, devices, isRequestingSending,
				isRequestingReceiving);
	}

	public boolean requestingSending()
	{
		return isRequestingSending;
	}

	public boolean requestingReceiving()
	{
		return isRequestingReceiving;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	public Status getStatus()
	{
		return this.status;
	}
}
