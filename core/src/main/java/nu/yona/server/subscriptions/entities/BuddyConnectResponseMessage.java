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

@Entity
public class BuddyConnectResponseMessage extends BuddyConnectMessage
{

	@Transient
	private UUID destinationID;
	private byte[] destinationIDCiphertext;

	@Transient
	private String nickname;
	private byte[] nicknameCiphertext;

	private BuddyAnonymized.Status status = BuddyAnonymized.Status.NOT_REQUESTED;
	private boolean isProcessed;

	// Default constructor is required for JPA
	public BuddyConnectResponseMessage()
	{
		super();
	}

	private BuddyConnectResponseMessage(UUID id, UUID userID, UUID vpnLoginID, String nickname, String message, UUID buddyID,
			UUID destinationID, BuddyAnonymized.Status status)
	{
		super(id, vpnLoginID, userID, message, buddyID);
		this.destinationID = destinationID;
		this.status = status;
		this.nickname = nickname;
	}

	public UUID getDestinationID()
	{
		return destinationID;
	}

	public String getNickname()
	{
		return nickname;
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

	public static BuddyConnectResponseMessage createInstance(UUID respondingUserID, UUID respondingUserVPNLoginID,
			UUID destinationID, String nickname, String message, UUID buddyID, BuddyAnonymized.Status status)
	{
		return new BuddyConnectResponseMessage(UUID.randomUUID(), respondingUserID, respondingUserVPNLoginID, nickname, message,
				buddyID, destinationID, status);
	}

	@Override
	public void encrypt(Encryptor encryptor)
	{
		super.encrypt(encryptor);
		destinationIDCiphertext = encryptor.encrypt(destinationID);
		nicknameCiphertext = encryptor.encrypt(nickname);
	}

	@Override
	public void decrypt(Decryptor decryptor)
	{
		super.decrypt(decryptor);
		destinationID = decryptor.decryptUUID(destinationIDCiphertext);
		nickname = decryptor.decryptString(nicknameCiphertext);
	}
}
