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

@Entity
public class BuddyConnectMessage extends Message {
	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private UUID buddyID;
	private byte[] buddyIDCiphertext;

	// Default constructor is required for JPA
	protected BuddyConnectMessage() {
		super(null, null);
	}

	protected BuddyConnectMessage(UUID id, UUID loginID, String message, UUID buddyID) {
		super(id, loginID);
		this.buddyID = buddyID;
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public UUID getBuddyID() {
		return buddyID;
	}

	@Override
	public void encrypt(Encryptor encryptor) {
		messageCiphertext = encryptor.encrypt(message);
		buddyIDCiphertext = encryptor.encrypt(buddyID);
	}

	@Override
	public void decrypt(Decryptor decryptor) {
		message = decryptor.decryptString(messageCiphertext);
		buddyID = decryptor.decryptUUID(buddyIDCiphertext);
	}
}
