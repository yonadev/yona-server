/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.crypto.pubkey.PublicKeyEncryptor;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;

@Entity
@Table(name = "MESSAGE_DESTINATIONS")
public class MessageDestination extends EntityWithUuid
{
	@Column(length = 1024)
	private byte[] publicKeyBytes;

	@Transient
	private PublicKey publicKey;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "messageDestination")
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

	public static MessageDestinationRepository getRepository()
	{
		return (MessageDestinationRepository) RepositoryProvider.getRepository(MessageDestination.class, UUID.class);
	}

	public static MessageDestination createInstance(PublicKey publicKey)
	{
		return new MessageDestination(UUID.randomUUID(), publicKey);
	}

	public void send(Message message)
	{
		message.encryptMessage(PublicKeyEncryptor.createInstance(loadPublicKey()));
		messages.add(message);
		message.setMessageDestination(this);
	}

	public void remove(Message message)
	{
		messages.remove(message);
	}

	public void remove(List<Message> messages)
	{
		this.messages.removeAll(messages);
	}

	public Page<Message> getMessages(Pageable pageable)
	{
		return Message.getRepository().findFromDestination(this, pageable);
	}

	public Page<Message> getReceivedMessages(Pageable pageable, boolean onlyUnreadMessages)
	{
		if (onlyUnreadMessages)
		{
			return Message.getRepository().findUnreadReceivedMessagesFromDestination(this, pageable);

		}
		return Message.getRepository().findReceivedMessagesFromDestination(this, pageable);
	}

	public Page<Message> getReceivedMessages(Pageable pageable, LocalDateTime earliestDateTime)
	{
		return Message.getRepository().findReceivedMessagesFromDestinationSinceDate(this, earliestDateTime, pageable);
	}

	private PublicKey loadPublicKey()
	{
		if (publicKey == null)
		{
			publicKey = PublicKeyUtil.publicKeyFromBytes(publicKeyBytes);
		}
		return publicKey;
	}

	public List<Message> getMessagesFromUser(UUID sentByUserAnonymizedId)
	{
		Optional<UUID> sentByUserAnonymizedIdInOptional = Optional.of(sentByUserAnonymizedId);
		return messages.stream().filter(message -> sentByUserAnonymizedIdInOptional.equals(message.getRelatedUserAnonymizedId()))
				.toList();
	}

	public void removeGoalConflictMessages(Goal goal)
	{
		messages.removeIf(message -> message instanceof GoalConflictMessage goalConflictMessage && goalConflictMessage.getGoal()
				.equals(goal));
	}

	public Page<Message> getActivityRelatedMessages(IntervalActivity intervalActivityEntity, Pageable pageable)
	{
		return Message.getRepository().findByIntervalActivity(this, intervalActivityEntity, pageable);
	}
}
