/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.crypto.PublicKeyEncryptor;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;

@Entity
@Table(name = "MESSAGE_DESTINATIONS")
public class MessageDestination extends EntityWithUuid
{
	public static MessageDestinationRepository getRepository()
	{
		return (MessageDestinationRepository) RepositoryProvider.getRepository(MessageDestination.class, UUID.class);
	}

	@Column(length = 1024)
	private byte[] publicKeyBytes;

	@Transient
	private PublicKey publicKey;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "message_destination_id", referencedColumnName = "id")
	private List<Message> messages;

	// Default constructor is required for JPA
	public MessageDestination()
	{
		super(null);
	}

	private MessageDestination(UUID id, PublicKey publicKey)
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

	public Page<Message> getMessages(Pageable pageable)
	{
		return Message.getRepository().findFromDestination(this.getID(), pageable);
	}

	public Page<Message> getReceivedMessages(Pageable pageable, boolean onlyUnreadMessages)
	{
		if (onlyUnreadMessages)
		{
			return Message.getRepository().findUnreadReceivedMessagesFromDestination(this.getID(), pageable);

		}
		return Message.getRepository().findReceivedMessagesFromDestination(this.getID(), pageable);
	}

	public Page<Message> getReceivedMessages(Pageable pageable, LocalDateTime earliestDateTime)
	{
		return Message.getRepository().findReceivedMessagesFromDestinationSinceDate(this.getID(), earliestDateTime, pageable);
	}

	private PublicKey loadPublicKey()
	{
		if (publicKey == null)
		{
			publicKey = PublicKeyUtil.publicKeyFromBytes(publicKeyBytes);
		}
		return publicKey;
	}

	public void removeMessagesFromUser(UUID sentByUserAnonymizedID)
	{
		Optional<UUID> sentByUserAnonymizedIDInOptional = Optional.of(sentByUserAnonymizedID);
		messages.removeIf(message -> sentByUserAnonymizedIDInOptional.equals(message.getRelatedUserAnonymizedID()));
	}

	public void removeGoalConflictMessages(Goal goal)
	{
		messages.removeIf(
				message -> message instanceof GoalConflictMessage && ((GoalConflictMessage) message).getGoal().equals(goal));
	}

	public Page<Message> getActivityRelatedMessages(long activityID, Pageable pageable)
	{
		return Message.getRepository().findByActivityID(getID(), activityID, pageable);
	}

	/*
	 * Returns the last sent message. ATTENTION: this message is encrypted, so not all properties are readable.
	 */
	public Message getLastSentMessage()
	{
		return messages.get(messages.size() - 1);
	}
}
