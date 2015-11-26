/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.messaging.entities.Message;

@Entity
public abstract class BuddyConnectMessage extends Message
{
	@Transient
	private UUID userID;
	private byte[] userIDCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private UUID buddyID;
	private byte[] buddyIDCiphertext;

	// Default constructor is required for JPA
	protected BuddyConnectMessage()
	{
		super(null, null);
	}

	protected BuddyConnectMessage(UUID id, UUID loginID, UUID userID, String message, UUID buddyID)
	{
		super(id, loginID);
		this.userID = userID;
		this.buddyID = buddyID;
		this.message = message;
	}

	public User getUser()
	{
		return User.getRepository().findOne(userID);
	}

	public String getMessage()
	{
		return message;
	}

	public UUID getBuddyID()
	{
		return buddyID;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		userIDCiphertext = encryptor.encrypt(userID);
		messageCiphertext = encryptor.encrypt(message);
		buddyIDCiphertext = encryptor.encrypt(buddyID);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		userID = decryptor.decryptUUID(userIDCiphertext);
		message = decryptor.decryptString(messageCiphertext);
		buddyID = decryptor.decryptUUID(buddyIDCiphertext);
	}
}
