/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.PublicKeyEncryptor;
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

	@OneToMany(cascade = CascadeType.ALL)
	private List<Message> messages;

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
		this.messages = new ArrayList<>();
	}

	public static MessageDestination createInstance(PublicKey publicKey)
	{
		return new MessageDestination(UUID.randomUUID(), publicKey);
	}

	public void send(Message message)
	{
		message.encryptMessage(PublicKeyEncryptor.createInstance(loadPublicKey()));
		messages.add(message);
	}

	public void remove(Message message)
	{
		messages.remove(message);
	}

	public List<Message> getAllMessages()
	{
		return Collections.unmodifiableList(messages);
	}

	private PublicKey loadPublicKey()
	{
		if (publicKey == null)
		{
			publicKey = PublicKeyUtil.publicKeyFromBytes(publicKeyBytes);
		}
		return publicKey;
	}

	public void removeMessagesFromUser(UUID fromUserLoginID)
	{
		messages.removeIf(message -> message.getRelatedVPNLoginID().equals(fromUserLoginID));
	}
}
