/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.pubkey.Decryptor;
import nu.yona.server.crypto.pubkey.Encryptor;
import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "MESSAGES")
public abstract class Message extends EntityWithId
{
	public static MessageRepository getRepository()
	{
		return (MessageRepository) RepositoryProvider.getRepository(Message.class, Long.class);
	}

	private byte[] decryptionInfo;

	@Type(type = "uuid-char")
	private final UUID relatedUserAnonymizedId;

	@ManyToOne
	private Message threadHeadMessage;

	@OneToMany(mappedBy = "threadHeadMessage", cascade = CascadeType.REMOVE)
	private final List<Message> messagesInThread;

	@ManyToOne
	private Message repliedMessage;

	@OneToMany(mappedBy = "repliedMessage", cascade = CascadeType.REMOVE)
	private final List<Message> replies;

	@ManyToOne
	private MessageDestination messageDestination;

	private final LocalDateTime creationTime;

	private final boolean isSentItem;

	private boolean isRead;

	protected Message()
	{
		this.relatedUserAnonymizedId = null;
		this.messagesInThread = null;
		this.replies = null;
		this.creationTime = null;
		this.isSentItem = false;
	}

	protected Message(UUID relatedUserAnonymizedId)
	{
		this(relatedUserAnonymizedId, false);
	}

	protected Message(UUID relatedUserAnonymizedId, boolean isSentItem)
	{
		this.relatedUserAnonymizedId = relatedUserAnonymizedId;
		this.creationTime = TimeUtil.utcNow();
		this.isSentItem = isSentItem;
		this.replies = new ArrayList<>();
		this.messagesInThread = new ArrayList<>();
	}

	public void encryptMessage(Encryptor encryptor)
	{
		decryptionInfo = encryptor.executeInCryptoSession(() -> encrypt());
	}

	public void decryptMessage(Decryptor decryptor)
	{
		decryptor.executeInCryptoSession(decryptionInfo, () -> decrypt());
	}

	public MessageDestination getMessageDestination()
	{
		return messageDestination;
	}

	public void setMessageDestination(MessageDestination messageDestination)
	{
		this.messageDestination = messageDestination;
	}

	public Optional<Message> getRepliedMessage()
	{
		return Optional.ofNullable(repliedMessage);
	}

	private void setRepliedMessage(Message repliedMessage)
	{
		this.repliedMessage = repliedMessage;
	}

	public void addReply(Message reply)
	{
		replies.add(reply);
		reply.setRepliedMessage(this);
	}

	public Message getThreadHeadMessage()
	{
		return threadHeadMessage;
	}

	protected void setThreadHeadMessage(Message threadHeadMessage)
	{
		this.threadHeadMessage = threadHeadMessage;
	}

	public void clearThreadHeadSelfReference()
	{
		if ((threadHeadMessage != null) && threadHeadMessage.getId() == getId())
		{
			threadHeadMessage = null;
		}
	}

	public void addMessageToThread(Message message)
	{
		replies.add(message);
		message.setThreadHeadMessage(this);
	}

	public LocalDateTime getCreationTime()
	{
		return creationTime;
	}

	public boolean isSentItem()
	{
		return isSentItem;
	}

	public boolean isRead()
	{
		return isRead;
	}

	public void setRead(boolean isRead)
	{
		this.isRead = isRead;
	}

	/**
	 * The ID of the related anonymized user. This is is one of:
	 * <ul>
	 * <li>If isSentItem == true, it is logically the target user</li>
	 * <li>The sender of this message</li>
	 * <li>In case of a system-originated message (e.g. a goal conflict message), it is the user for which this message is sent</li>
	 * </ul>
	 * 
	 * @return The ID of the related anonymized user. Might be null if that user was already deleted at the time this message was
	 *         sent on behalf of that user.
	 */
	public Optional<UUID> getRelatedUserAnonymizedId()
	{
		return Optional.ofNullable(relatedUserAnonymizedId);
	}

	protected abstract void encrypt();

	protected abstract void decrypt();
}
