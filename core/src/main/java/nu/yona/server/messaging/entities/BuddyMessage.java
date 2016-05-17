/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.subscriptions.entities.User;

@Entity
public abstract class BuddyMessage extends Message
{
	@Transient
	private UUID senderUserID;
	private byte[] senderUserIDCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private String senderNickname;
	private byte[] senderNicknameCiphertext;

	// Default constructor is required for JPA
	protected BuddyMessage()
	{
		super(null, null);
	}

	protected BuddyMessage(UUID id, UUID sendingUserID, UUID sendingUserAnonymizedID, String sendingUserNickname, String message)
	{
		super(id, sendingUserAnonymizedID);
		this.senderUserID = sendingUserID;
		this.senderNickname = sendingUserNickname;
		this.message = message;
	}

	/**
	 * Returns the user sending this message.
	 * 
	 * @return The user sending this message. Might be null if that user is already deleted.
	 */
	public User getSenderUser()
	{
		return (senderUserID == null) ? null : User.getRepository().findOne(senderUserID);
	}

	/**
	 * Returns the ID of the user sending this message.
	 * 
	 * @return The ID of the user sending this message. Might be null if that user was already deleted at the time this message
	 *         was sent on behalf of that user.
	 */
	public UUID getSenderUserID()
	{
		return senderUserID;
	}

	public String getMessage()
	{
		return message;
	}

	public String getSenderNickname()
	{
		return senderNickname;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		senderUserIDCiphertext = encryptor.encrypt(senderUserID);
		messageCiphertext = encryptor.encrypt(message);
		senderNicknameCiphertext = encryptor.encrypt(senderNickname);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		senderUserID = decryptor.decryptUUID(senderUserIDCiphertext);
		message = decryptor.decryptString(messageCiphertext);
		senderNickname = decryptor.decryptString(senderNicknameCiphertext);
	}
}
