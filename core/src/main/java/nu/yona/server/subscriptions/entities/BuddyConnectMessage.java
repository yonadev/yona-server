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
	private UUID buddyId;
	private byte[] buddyIdCiphertext;

	// Default constructor is required for JPA
	protected BuddyConnectMessage()
	{
		super();
	}

	protected BuddyConnectMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderNickname, String message,
			UUID buddyId)
	{
		super(senderUserId, senderUserAnonymizedId, senderNickname, message);
		this.buddyId = buddyId;
	}

	public UUID getBuddyId()
	{
		return buddyId;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		super.encrypt(encryptor);
		buddyIdCiphertext = encryptor.encrypt(buddyId);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		super.decrypt(decryptor);
		buddyId = decryptor.decryptUuid(buddyIdCiphertext);
	}
}
