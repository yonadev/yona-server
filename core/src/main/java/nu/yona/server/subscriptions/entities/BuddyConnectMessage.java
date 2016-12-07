/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public abstract class BuddyConnectMessage extends BuddyMessage
{
	@Transient
	private UUID buddyID;
	private byte[] buddyIDCiphertext;

	// Default constructor is required for JPA
	protected BuddyConnectMessage()
	{
		super();
	}

	protected BuddyConnectMessage(UUID senderUserID, UUID senderUserAnonymizedID, String senderNickname, String message,
			UUID buddyID)
	{
		super(senderUserID, senderUserAnonymizedID, senderNickname, message);
		this.buddyID = buddyID;
	}

	public UUID getBuddyID()
	{
		return buddyID;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		super.encrypt(encryptor);
		buddyIDCiphertext = encryptor.encrypt(buddyID);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		super.decrypt(decryptor);
		buddyID = decryptor.decryptUUID(buddyIDCiphertext);
	}
}
