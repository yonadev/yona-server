/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;

@Entity
public class BuddyConnectResponseMessage extends BuddyConnectMessage
{
	private BuddyAnonymized.Status status = BuddyAnonymized.Status.NOT_REQUESTED;
	private boolean isProcessed;

	// Default constructor is required for JPA
	public BuddyConnectResponseMessage()
	{
		super();
	}

	private BuddyConnectResponseMessage(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname, String message,
			UUID buddyID, BuddyAnonymized.Status status)
	{
		super(senderUserID, senderUserAnonymizedID, senderNickname, message, buddyID);
		this.status = status;
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

	public static BuddyConnectResponseMessage createInstance(UUID senderUserID, UUID senderUserAnonymizedID,
			String senderNickname, String message, UUID buddyID, BuddyAnonymized.Status status)
	{
		return new BuddyConnectResponseMessage(senderUserID, senderUserAnonymizedID, senderNickname, message, buddyID, status);
	}
}