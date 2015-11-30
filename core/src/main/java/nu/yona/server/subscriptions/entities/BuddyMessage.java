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
public abstract class BuddyMessage extends Message
{
	@Transient
	private UUID userID;
	private byte[] userIDCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private String nickname;
	private byte[] nicknameCiphertext;

	// Default constructor is required for JPA
	protected BuddyMessage()
	{
		super(null, null);
	}

	protected BuddyMessage(UUID id, UUID loginID, UUID userID, String nickname, String message)
	{
		super(id, loginID);
		this.userID = userID;
		this.nickname = nickname;
		this.message = message;
	}

	/*
	 * May be {@literal null} if the user has been deleted.
	 */
	public User getUser()
	{
		return User.getRepository().findOne(userID);
	}

	public UUID getUserID()
	{
		return userID;
	}

	public String getMessage()
	{
		return message;
	}

	public String getNickname()
	{
		return nickname;
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		userIDCiphertext = encryptor.encrypt(userID);
		messageCiphertext = encryptor.encrypt(message);
		nicknameCiphertext = encryptor.encrypt(nickname);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		userID = decryptor.decryptUUID(userIDCiphertext);
		message = decryptor.decryptString(messageCiphertext);
		nickname = decryptor.decryptString(nicknameCiphertext);
	}
}
