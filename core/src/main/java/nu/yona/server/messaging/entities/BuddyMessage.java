/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.subscriptions.entities.User;

@Entity
public abstract class BuddyMessage extends Message
{
	@Transient
	private UUID senderUserId;
	private byte[] senderUserIdCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private String senderNickname;
	private byte[] senderNicknameCiphertext;

	// Default constructor is required for JPA
	protected BuddyMessage()
	{
		super(null);
	}

	protected BuddyMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderUserNickname, String message)
	{
		this(senderUserId, senderUserAnonymizedId, senderUserNickname, false, message);
	}

	protected BuddyMessage(UUID senderUserId, UUID senderUserAnonymizedId, String senderUserNickname, boolean isSentItem,
			String message)
	{
		super(senderUserAnonymizedId, isSentItem);
		this.senderUserId = senderUserId;
		this.senderNickname = senderUserNickname;
		this.message = message;
	}

	/**
	 * Returns the user sending this message.
	 * 
	 * @return The user sending this message. Might be null if that user is already deleted.
	 */
	public Optional<User> getSenderUser()
	{
		return (senderUserId == null) ? Optional.empty() : Optional.ofNullable(User.getRepository().findOne(senderUserId));
	}

	/**
	 * Returns the ID of the user sending this message.
	 * 
	 * @return The ID of the user sending this message. Might be null if that user was already deleted at the time this message
	 *         was sent on behalf of that user.
	 */
	public UUID getSenderUserId()
	{
		return senderUserId;
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
	public void encrypt()
	{
		senderUserIdCiphertext = CryptoUtil.encryptUuid(senderUserId);
		messageCiphertext = CryptoUtil.encryptString(message);
		senderNicknameCiphertext = CryptoUtil.encryptString(senderNickname);
	}

	@Override
	public void decrypt()
	{
		senderUserId = CryptoUtil.decryptUuid(senderUserIdCiphertext);
		message = CryptoUtil.decryptString(messageCiphertext);
		senderNickname = CryptoUtil.decryptString(senderNicknameCiphertext);
	}
}
