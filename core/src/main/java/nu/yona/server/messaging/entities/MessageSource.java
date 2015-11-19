/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.ByteFieldEncrypter;
import nu.yona.server.crypto.PublicKeyDecryptor;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "MESSAGE_SOURCES")
public class MessageSource extends EntityWithID
{
	public static MessageSourceRepository getRepository()
	{
		return (MessageSourceRepository) RepositoryProvider.getRepository(MessageSource.class, UUID.class);
	}

	@Convert(converter = ByteFieldEncrypter.class)
	@Column(length = 1024)
	private byte[] privateKeyBytes;

	@OneToOne(cascade = CascadeType.ALL)
	private MessageDestination messageDestination;

	@Transient
	private PrivateKey privateKey;

	// Default constructor is required for JPA
	public MessageSource()
	{
		super(null);
	}

	public MessageSource(UUID id, PrivateKey privateKey, MessageDestination messageDestination)
	{
		super(id);
		this.messageDestination = messageDestination;
		this.privateKeyBytes = PublicKeyUtil.privateKeyToBytes(privateKey);
	}

	public static MessageSource createInstance()
	{
		KeyPair pair = PublicKeyUtil.generateKeyPair();

		MessageDestination messageDestination = MessageDestination.createInstance(pair.getPublic());
		return new MessageSource(UUID.randomUUID(), pair.getPrivate(), messageDestination);
	}

	public MessageDestination getDestination()
	{
		return messageDestination;
	}

	public List<Message> getAllMessages()
	{
		List<Message> decryptedMessages = messageDestination.getAllMessages();
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(loadPrivateKey());
		decryptedMessages.stream().forEach(m -> m.decryptMessage(decryptor));
		return decryptedMessages;
	}

	private PrivateKey loadPrivateKey()
	{
		if (privateKey == null)
		{
			privateKey = PublicKeyUtil.privateKeyFromBytes(privateKeyBytes);
		}
		return privateKey;
	}

	public Message getMessage(UUID idToFetch)
	{
		Message message = Message.getRepository().findOne(idToFetch);
		message.decryptMessage(PublicKeyDecryptor.createInstance(loadPrivateKey()));
		return message;
	}

	public void touch()
	{
		privateKeyBytes = Arrays.copyOf(privateKeyBytes, privateKeyBytes.length);
	}
}
