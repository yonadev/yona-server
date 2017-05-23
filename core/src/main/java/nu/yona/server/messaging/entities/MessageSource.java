/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.crypto.pubkey.PublicKeyDecryptor;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.crypto.seckey.ByteFieldEncryptor;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.messaging.service.MessageNotFoundException;

@Entity
@Table(name = "MESSAGE_SOURCES")
public class MessageSource extends EntityWithUuid
{
	@Convert(converter = ByteFieldEncryptor.class)
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

	private MessageSource(UUID id, PrivateKey privateKey, MessageDestination messageDestination)
	{
		super(id);
		this.messageDestination = messageDestination;
		this.privateKeyBytes = toArrayWithTouchByte(privateKey);
	}

	public static MessageSourceRepository getRepository()
	{
		return (MessageSourceRepository) RepositoryProvider.getRepository(MessageSource.class, UUID.class);
	}

	public static MessageSource createInstance()
	{
		KeyPair pair = PublicKeyUtil.generateKeyPair();

		MessageDestination messageDestination = MessageDestination.createInstance(pair.getPublic());
		return new MessageSource(UUID.randomUUID(), pair.getPrivate(), messageDestination);
	}

	private static byte[] toArrayWithTouchByte(PrivateKey privateKey)
	{
		// Extract the private key bytes to the 1 position of an array. The 0 position is reserved to do the touch.
		byte[] privateKeyBytes = PublicKeyUtil.privateKeyToBytes(privateKey);
		byte[] extendedArray = new byte[privateKeyBytes.length + 1];
		System.arraycopy(privateKeyBytes, 0, extendedArray, 1, privateKeyBytes.length);
		return extendedArray;
	}

	public MessageDestination getDestination()
	{
		return messageDestination;
	}

	public Page<Message> getMessages(Pageable pageable)
	{
		Page<Message> messages = messageDestination.getMessages(pageable);
		decryptMessagePage(messages);
		return messages;
	}

	public Page<Message> getReceivedMessages(Pageable pageable, boolean onlyUnreadMessages)
	{
		Page<Message> messages = messageDestination.getReceivedMessages(pageable, onlyUnreadMessages);
		decryptMessagePage(messages);
		return messages;
	}

	public Page<Message> getReceivedMessages(Pageable pageable, LocalDateTime earliestDateTime)
	{
		Page<Message> messages = messageDestination.getReceivedMessages(pageable, earliestDateTime);
		decryptMessagePage(messages);
		return messages;
	}

	private void decryptMessagePage(Page<Message> messages)
	{
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(loadPrivateKey());
		messages.forEach(m -> m.decryptMessage(decryptor));
	}

	private PrivateKey loadPrivateKey()
	{
		if (privateKey == null)
		{
			privateKey = PublicKeyUtil.privateKeyFromBytes(Arrays.copyOfRange(privateKeyBytes, 1, privateKeyBytes.length));
		}
		return privateKey;
	}

	public Message getMessage(long idToFetch)
	{
		Message message = Message.getRepository().findOne(idToFetch);
		if (message == null)
		{
			throw MessageNotFoundException.messageNotFound(idToFetch);
		}

		message.decryptMessage(PublicKeyDecryptor.createInstance(loadPrivateKey()));
		return message;
	}

	public MessageSource touch()
	{
		privateKeyBytes[0]++;
		return this;
	}

	public Page<Message> getActivityRelatedMessages(IntervalActivity intervalActivityEntity, Pageable pageable)
	{
		Page<Message> messages = messageDestination.getActivityRelatedMessages(intervalActivityEntity, pageable);
		decryptMessagePage(messages);
		return messages;
	}
}
