/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.service.BuddyServiceException;

@Entity
public class BuddyConnectRequestMessage extends BuddyConnectMessage
{
	private boolean isRequestingSending;
	private boolean isRequestingReceiving;

	private Status status;

	// Default constructor is required for JPA
	public BuddyConnectRequestMessage()
	{
		super();
	}

	private BuddyConnectRequestMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname, String message,
			UUID buddyId, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		super(senderUserId, senderUserAnonymizedId, senderNickname, message, buddyId);

		if (senderUserId == null)
		{
			throw BuddyServiceException.requestingUserCannotBeNull();
		}

		this.status = Status.REQUESTED;
		this.isRequestingSending = isRequestingSending;
		this.isRequestingReceiving = isRequestingReceiving;
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

	public static BuddyConnectRequestMessage createInstance(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname,
			String message, UUID buddyId, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		return new BuddyConnectRequestMessage(senderUserId, senderUserAnonymizedId, senderNickname, message, buddyId,
				isRequestingSending, isRequestingReceiving);
	}
}
