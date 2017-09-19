/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@Entity
public class BuddyDisconnectMessage extends BuddyMessage
{
	private boolean isProcessed;
	private DropBuddyReason reason;

	public BuddyDisconnectMessage(UUID senderUserId, UUID senderAnonymizedUserId, String senderNickname, String message,
			DropBuddyReason reason)
	{
		super(senderUserId, senderAnonymizedUserId, senderNickname, message);
		this.reason = reason;
	}

	// Default constructor is required for JPA
	public BuddyDisconnectMessage()
	{
		super();
	}

	public static BuddyDisconnectMessage createInstance(UUID senderUserId, UUID senderAnonymizedUserId, String senderNickname,
			String message, DropBuddyReason reason)
	{
		return new BuddyDisconnectMessage(senderUserId, senderAnonymizedUserId, senderNickname, message, reason);
	}

	public DropBuddyReason getReason()
	{
		return reason;
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
