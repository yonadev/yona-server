/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.model.RepositoryProvider;

@Entity
@Table(name = "MESSAGES")
public abstract class Message {
	public static MessageRepository getRepository() {
		return (MessageRepository) RepositoryProvider.getRepository(Message.class, UUID.class);
	}

	@Id
	@Type(type = "uuid-char")
	private UUID id;

	public Message() {
		// Default constructor is required for JPA
	}

	protected Message(UUID id) {
		this.id = id;
	}

	public UUID getID() {
		return id;
	}

	public abstract void encrypt(Encryptor encryptor);

	public abstract void decrypt(Decryptor decryptor);
}
