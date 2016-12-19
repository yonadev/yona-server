/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public class BuddyInfoChangeMessage extends BuddyMessage
{
	@Transient
	private String newNickname;
	private byte[] newNicknameCiphertext;
	private boolean isProcessed;

	public BuddyInfoChangeMessage(UUID id, UUID senderUserID, UUID senderAnonymizedUserID, String senderNickname, String message,
			String newNickname)
	{
		super(id, senderUserID, senderAnonymizedUserID, senderNickname, message);
		this.newNickname = newNickname;
	}

	// Default constructor is required for JPA
	public BuddyInfoChangeMessage()
	{
		super();
	}

	public String getNewNickname()
	{
		return newNickname;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	public void setProcessed()
	{
		this.isProcessed = true;
	}

	@Override
	public void encrypt()
	{
		super.encrypt();
		newNicknameCiphertext = CryptoUtil.encryptString(newNickname);
	}

	@Override
	public void decrypt()
	{
		super.decrypt();
		newNickname = CryptoUtil.decryptString(newNicknameCiphertext);
	}

	public static BuddyInfoChangeMessage createInstance(UUID senderUserID, UUID senderAnonymizedUserID, String senderNickname,
			String message, String newNickname)
	{
		return new BuddyInfoChangeMessage(UUID.randomUUID(), senderUserID, senderAnonymizedUserID, senderNickname, message,
				newNickname);
	}
}
