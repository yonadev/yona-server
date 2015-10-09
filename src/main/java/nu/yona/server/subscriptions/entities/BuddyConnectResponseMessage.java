/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.subscriptions.entities.Buddy.Status;

@Entity
public class BuddyConnectResponseMessage extends Message {

	@Transient
	private UUID respondingUserID;
	private byte[] respondingUserIDCiphertext;

	@Transient
	private UUID accessorID;
	private byte[] accessorIDCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private UUID buddyID;
	private byte[] buddyIDCiphertext;

	@Transient
	private UUID destinationID;
	private byte[] destinationIDCiphertext;
	private Status status = Status.NOT_REQUESTED;
	private boolean isProcessed;

	// Default constructor is required for JPA
	public BuddyConnectResponseMessage() {
		super(null);
	}

	private BuddyConnectResponseMessage(UUID id, UUID respondingUserID, UUID accessorID, String message, UUID buddyID,
			UUID destinationID, Status status) {
		super(id);
		this.respondingUserID = respondingUserID;
		this.accessorID = accessorID;
		this.message = message;
		this.buddyID = buddyID;
		this.destinationID = destinationID;
		this.status = status;
	}

	public User getRespondingUser() {
		return User.getRepository().findOne(respondingUserID);
	}

	public UUID getAccessorID() {
		return accessorID;
	}

	public String getMessage() {
		return message;
	}

	public UUID getBuddyID() {
		return buddyID;
	}

	public UUID getDestinationID() {
		return destinationID;
	}

	public Status getStatus() {
		return status;
	}

	public boolean isProcessed() {
		return isProcessed;
	}

	public void setProcessed() {
		this.isProcessed = true;
	}

	public static BuddyConnectResponseMessage createInstance(User respondingUser, UUID destinationID, String message,
			UUID buddyID, Status status) {
		return new BuddyConnectResponseMessage(UUID.randomUUID(), respondingUser.getID(),
				respondingUser.getAccessorID(), message, buddyID, destinationID, status);
	}

	@Override
	public void encrypt(Encryptor encryptor) {
		respondingUserIDCiphertext = encryptor.encrypt(respondingUserID);
		accessorIDCiphertext = encryptor.encrypt(accessorID);
		messageCiphertext = encryptor.encrypt(message);
		buddyIDCiphertext = encryptor.encrypt(buddyID);
		destinationIDCiphertext = encryptor.encrypt(destinationID);
	}

	@Override
	public void decrypt(Decryptor decryptor) {
		respondingUserID = decryptor.decryptUUID(respondingUserIDCiphertext);
		accessorID = decryptor.decryptUUID(accessorIDCiphertext);
		message = decryptor.decryptString(messageCiphertext);
		buddyID = decryptor.decryptUUID(buddyIDCiphertext);
		destinationID = decryptor.decryptUUID(destinationIDCiphertext);
	}
}