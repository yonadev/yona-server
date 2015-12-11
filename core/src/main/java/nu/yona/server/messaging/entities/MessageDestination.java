/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.security.PublicKey;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "MESSAGE_DESTINATIONS")
public class MessageDestination extends EntityWithID
{
	public static MessageDestinationRepository getRepository()
	{
		return (MessageDestinationRepository) RepositoryProvider.getRepository(MessageDestination.class, UUID.class);
	}

	@Column(length = 1024)
	private byte[] publicKeyBytes;

	@Transient
	private PublicKey publicKey;

	// Default constructor is required for JPA
	public MessageDestination()
	{
		super(null);
	}

	public MessageDestination(UUID id, PublicKey publicKey)
	{
		super(id);
		this.publicKeyBytes = PublicKeyUtil.publicKeyToBytes(publicKey);
	}

	public static MessageDestination createInstance(PublicKey publicKey)
	{
		return new MessageDestination(UUID.randomUUID(), publicKey);
	}

	public Page<Message> getMessages(Pageable pageable)
	{
		return Message.getRepository().findFromDestination(this.getID(), pageable);
	}

	private PublicKey loadPublicKey()
	{
		if (publicKey == null)
		{
			publicKey = PublicKeyUtil.publicKeyFromBytes(publicKeyBytes);
		}
		return publicKey;
	}

	public PublicKey getPublicKey()
	{
		return loadPublicKey();
	}
}
