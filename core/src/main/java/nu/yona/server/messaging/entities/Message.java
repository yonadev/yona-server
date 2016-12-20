/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "MESSAGES")
public abstract class Message extends EntityWithId
{
	@Type(type = "uuid-char")
	private final UUID relatedUserAnonymizedId;

	private Long threadHeadMessageId;

	private Long repliedMessageId;

	@Type(type = "uuid-char")
	@Column(name = "message_destination_id")
	private UUID messageDestinationId;

	private final LocalDateTime creationTime;

	private final boolean isSentItem;

	private boolean isRead;

	public static MessageRepository getRepository()
	{
		return (MessageRepository) RepositoryProvider.getRepository(Message.class, Long.class);
	}

	/**
	 * This is the only constructor, to ensure that subclasses don't accidentally omit the ID.
	 * 
	 * @param id The ID of the entity
	 * @param relatedUserAnonymizedId The ID of the related anonymized user. This is either the sender of this message or the one
	 *            for which this message is sent (e.g in case of a goal conflict message).
	 */
	protected Message(UUID relatedUserAnonymizedId)
	{
		this(relatedUserAnonymizedId, false);
	}

	protected Message(UUID relatedUserAnonymizedId, boolean isSentItem)
	{
		super();

		this.relatedUserAnonymizedId = relatedUserAnonymizedId;
		this.creationTime = TimeUtil.utcNow();
		this.isSentItem = isSentItem;
	}

	public void encryptMessage(Encryptor encryptor)
	{
		encrypt(encryptor);
	}

	public void decryptMessage(Decryptor decryptor)
	{
		decrypt(decryptor);
	}

	protected void setRepliedMessageId(Optional<Long> repliedMessageId)
	{
		this.repliedMessageId = repliedMessageId.orElse(null);
	}

	protected void setThreadHeadMessageId(Optional<Long> threadHeadMessageId)
	{
		this.threadHeadMessageId = threadHeadMessageId.orElse(null);
	}

	public long getThreadHeadMessageId()
	{
		return threadHeadMessageId != null ? threadHeadMessageId : getId();
	}

	public Optional<Long> getRepliedMessageId()
	{
		return Optional.ofNullable(repliedMessageId);
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
	 * The ID of the related anonymized user. This is either the sender of this message or the one for which this message is sent
	 * (e.g in case of a goal conflict message).
	 * 
	 * @return The ID of the related anonymized user. Might be null if that user was already deleted at the time this message was
	 *         sent on behalf of that user.
	 */
	public Optional<UUID> getRelatedUserAnonymizedId()
	{
		return Optional.ofNullable(relatedUserAnonymizedId);
	}

	protected abstract void encrypt(Encryptor encryptor);

	protected abstract void decrypt(Decryptor decryptor);

	public void setSelfThreadHeadIfRequired()
	{
		if (threadHeadMessageId == null)
		{
			setSelfThreadHead();
		}
	}

	private void setSelfThreadHead()
	{
		long id = getId();
		assert id != 0;
		threadHeadMessageId = id;
	}
}
