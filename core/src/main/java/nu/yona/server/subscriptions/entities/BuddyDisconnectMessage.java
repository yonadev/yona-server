/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import javax.persistence.Entity;

import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@Entity
public class BuddyDisconnectMessage extends BuddyMessage
{
	private boolean isProcessed;
	private DropBuddyReason reason;

	public BuddyDisconnectMessage(BuddyInfoParameters buddyInfoParameters, String message, DropBuddyReason reason)
	{
		super(buddyInfoParameters, message);
		this.reason = reason;
	}

	// Default constructor is required for JPA
	public BuddyDisconnectMessage()
	{
		super();
	}

	public static BuddyDisconnectMessage createInstance(BuddyInfoParameters buddyInfoParameters, String message,
			DropBuddyReason reason)
	{
		return new BuddyDisconnectMessage(buddyInfoParameters, message, reason);
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
