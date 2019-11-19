/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.pubkey.Decryptor;
import nu.yona.server.crypto.pubkey.Encryptor;
import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "MESSAGES")
public abstract class Message extends EntityWithId
{
	private byte[] decryptionInfo;

	@Type(type = "uuid-char")
	private final UUID relatedUserAnonymizedId;

	@ManyToOne
	private Message threadHeadMessage;

	@OneToMany(mappedBy = "threadHeadMessage")
	private final List<Message> messagesInThread;

	@ManyToOne
	private Message repliedMessage;

	@OneToMany(mappedBy = "repliedMessage")
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

	protected Message(Optional<UUID> relatedUserAnonymizedId)
	{
		this(relatedUserAnonymizedId, false);
	}

	protected Message(Optional<UUID> relatedUserAnonymizedId, boolean isSentItem)
	{
		this.relatedUserAnonymizedId = relatedUserAnonymizedId.orElse(null);
		this.creationTime = TimeUtil.utcNow();
		this.isSentItem = isSentItem;
		this.replies = new ArrayList<>();
		this.messagesInThread = new ArrayList<>();
	}

	/**
	 * Copy constructor. See {@link nu.yona.server.messaging.entities.Message#duplicate()}
	 * 
	 * @param original Message to copy.
	 */
	protected Message(Message original)
	{
		this.decryptionInfo = null; // Set when encrypting the message
		this.relatedUserAnonymizedId = original.relatedUserAnonymizedId;
		this.threadHeadMessage = null;
		this.messagesInThread = new ArrayList<>();
		this.repliedMessage = null;
		this.replies = new ArrayList<>();
		this.messageDestination = null;
		this.creationTime = original.creationTime;
		this.isSentItem = original.isSentItem;
		this.isRead = original.isRead;
	}

	public static MessageRepository getRepository()
	{
		return (MessageRepository) RepositoryProvider.getRepository(Message.class, Long.class);
	}

	public void encryptMessage(Encryptor encryptor)
	{
		decryptionInfo = encryptor.executeInCryptoSession(this::encrypt);
	}

	public void decryptMessage(Decryptor decryptor)
	{
		decryptor.executeInCryptoSession(decryptionInfo, this::decrypt);
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

	protected void clearThreadHeadSelfReference()
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
	 * <li>In case of a system-originated message (e.g. a goal conflict message), it is the user for which this message is
	 * sent</li>
	 * </ul>
	 * 
	 * @return The ID of the related anonymized user. Might be null if that user was already deleted at the time this message was
	 *         sent on behalf of that user.
	 */
	public Optional<UUID> getRelatedUserAnonymizedId()
	{
		return Optional.ofNullable(relatedUserAnonymizedId);
	}

	public void prepareForDelete()
	{
		clearThreadHeadSelfReference();
		threadHeadMessage = null;
	}

	public Set<Message> getMessagesToBeCascadinglyDeleted()
	{
		Set<Message> messagesToBeCascadinglyDeleted = new HashSet<>();
		messagesToBeCascadinglyDeleted.addAll(replies);
		messagesToBeCascadinglyDeleted.addAll(
				replies.stream().flatMap(m -> m.getMessagesToBeCascadinglyDeleted().stream()).collect(Collectors.toSet()));
		return messagesToBeCascadinglyDeleted;
	}

	protected abstract void encrypt();

	protected abstract void decrypt();

	/**
	 * Creates a duplicate of the given messages, ready to be sent to a destination. Everything is identical to the original one,
	 * except for:
	 * <ul>
	 * <li>The ID, which will be 0</li>
	 * <li>References to other messages (e.g. replies). If this is ever necessary, it needs to be designed and built</li>
	 * <li>Reference to the message destination. The duplicate is ready to be sent and does not have a destination yet.</li>
	 * <li>Encrypted fields and decryptionInfo. These are set while encrypting the message when sending it to a destination</li>
	 * </ul>
	 * For this to work, the message class needs to have a public copy constructor. Currently, it is only implemented for message
	 * sent to the direct message destination of a user.
	 * 
	 * @return The duplicate message
	 */
	public Message duplicate()
	{
		return duplicate(this);
	}

	private static Message duplicate(Message message)
	{
		try
		{
			return message.getClass().getConstructor(message.getClass()).newInstance(message);
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
