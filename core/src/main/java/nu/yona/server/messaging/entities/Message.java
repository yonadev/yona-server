/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "MESSAGES")
public abstract class Message extends EntityWithID
{
	@Transient
	private UUID relatedLoginID;
	private byte[] relatedUserAnonymizedIDCiphertext;

	public static MessageRepository getRepository()
	{
		return (MessageRepository) RepositoryProvider.getRepository(Message.class, UUID.class);
	}

	/**
	 * This is the only constructor, to ensure that subclasses don't accidentally omit the ID.
	 * 
	 * @param id The ID of the entity
	 */
	protected Message(UUID id, UUID relatedUserAnonymizedID)
	{
		super(id);
		if (id != null && relatedUserAnonymizedID == null)
		{
			throw new IllegalArgumentException("relatedUserAnonymizedID cannot be null");
		}
		this.relatedLoginID = relatedUserAnonymizedID;
	}

	public void encryptMessage(Encryptor encryptor)
	{
		relatedUserAnonymizedIDCiphertext = encryptor.encrypt(relatedLoginID);
		encrypt(encryptor);
	}

	public void decryptMessage(Decryptor decryptor)
	{
		relatedLoginID = decryptor.decryptUUID(relatedUserAnonymizedIDCiphertext);
		decrypt(decryptor);
	}

	public UUID getRelatedLoginID()
	{
		return relatedLoginID;
	}

	protected abstract void encrypt(Encryptor encryptor);

	protected abstract void decrypt(Decryptor decryptor);
}
