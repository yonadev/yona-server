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

	private BuddyConnectResponseMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname, String message,
			UUID buddyId, BuddyAnonymized.Status status)
	{
		super(senderUserId, senderUserAnonymizedId, senderNickname, message, buddyId);
		this.status = status;
	}

	public static BuddyConnectResponseMessage createInstance(UUID senderUserId, UUID senderUserAnonymizedId,
			String senderNickname, String message, UUID buddyId, BuddyAnonymized.Status status)
	{
		return new BuddyConnectResponseMessage(senderUserId, senderUserAnonymizedId, senderNickname, message, buddyId, status);
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
