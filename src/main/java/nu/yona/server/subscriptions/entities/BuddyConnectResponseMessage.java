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
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.subscriptions.entities.Buddy.Status;

@Entity
public class BuddyConnectResponseMessage extends Message {

	@ManyToOne
	private User respondingUser;

	@Transient
	private UUID accessorID;
	private byte[] accessorIDCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private long buddyID;
	private byte[] buddyIDCiphertext;

	private Status status = Status.NOT_REQUESTED;
	private boolean isProcessed;

	@Transient
	private long destinationID;
	private byte[] destinationIDCiphertext;

	// Default constructor is required for JPA
	public BuddyConnectResponseMessage() {
		super(null);
	}

	private BuddyConnectResponseMessage(UUID id, User respondingUser, UUID accessorID, long destinationID,
			String message, long buddyID, Status status) {
		super(id);
		this.respondingUser = respondingUser;
		this.accessorID = accessorID;
		this.destinationID = destinationID;
		this.message = message;
		this.buddyID = buddyID;
		this.status = status;
	}

	public User getRespondingUser() {
		return respondingUser;
	}

	public UUID getAccessorID() {
		return accessorID;
	}

	public long getDestinationID() {
		return destinationID;
	}

	public String getMessage() {
		return message;
	}

	public long getBuddyID() {
		return buddyID;
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

	public static BuddyConnectResponseMessage createInstance(User respondingUser, long destinationID, String message,
			long buddyID, Status status) {
		return new BuddyConnectResponseMessage(UUID.randomUUID(), respondingUser, respondingUser.getAccessorID(),
				destinationID, message, buddyID, status);
	}

	@Override
	public void encrypt(Encryptor encryptor) {
		accessorIDCiphertext = encryptor.encrypt(accessorID);
		messageCiphertext = encryptor.encrypt(message);
		buddyIDCiphertext = encryptor.encrypt(buddyID);
		destinationIDCiphertext = encryptor.encrypt(destinationID);
	}

	@Override
	public void decrypt(Decryptor decryptor) {
		message = decryptor.decryptString(messageCiphertext);
		accessorID = decryptor.decryptUUID(accessorIDCiphertext);
		buddyID = decryptor.decryptLong(buddyIDCiphertext);
		destinationID = decryptor.decryptLong(destinationIDCiphertext);
	}
}
