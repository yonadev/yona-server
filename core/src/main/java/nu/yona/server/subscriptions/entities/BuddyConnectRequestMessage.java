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

	private BuddyConnectRequestMessage(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname, String message,
			UUID buddyID, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		super(senderUserID, senderUserAnonymizedID, senderNickname, message, buddyID);

		if (senderUserID == null)
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

	public static BuddyConnectRequestMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname,
			String message, UUID buddyID, boolean isRequestingSending, boolean isRequestingReceiving)
	{
		return new BuddyConnectRequestMessage(senderUserID, senderUserAnonymizedID, senderNickname, message, buddyID,
				isRequestingSending, isRequestingReceiving);
	}
}
