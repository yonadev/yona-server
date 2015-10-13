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
import javax.persistence.Table;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "MESSAGES")
public abstract class Message extends EntityWithID {
	public static MessageRepository getRepository() {
		return (MessageRepository) RepositoryProvider.getRepository(Message.class, UUID.class);
	}

	/**
	 * This is the only constructor, to ensure that subclasses don't
	 * accidentally omit the ID.
	 * 
	 * @param id
	 *            The ID of the entity
	 */
	protected Message(UUID id) {
		super(id);
	}

	public abstract void encrypt(Encryptor encryptor);

	public abstract void decrypt(Decryptor decryptor);
}
