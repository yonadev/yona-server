/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.crypto.UUIDFieldEncrypter;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

@Entity
@Table(name = "BUDDIES")
public class Buddy extends EntityWithUuid
{
	public static BuddyRepository getRepository()
	{
		return (BuddyRepository) RepositoryProvider.getRepository(Buddy.class, UUID.class);
	}

	@Type(type = "uuid-char")
	@Column(name = "owning_user_private_id")
	private UUID owningUserPrivateId;

	@Column(nullable = true)
	private int touchVersion;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID userId;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID buddyAnonymizedId;

	@Convert(converter = StringFieldEncrypter.class)
	private String nickname;

	// Default constructor is required for JPA
	public Buddy()
	{
		super(null);
	}

	private Buddy(UUID id, UUID userId, String nickname, UUID buddyAnonymizedId)
	{
		super(id);
		Objects.requireNonNull(userId);
		Objects.requireNonNull(nickname);
		Objects.requireNonNull(buddyAnonymizedId);
		this.userId = userId;
		this.nickname = nickname;
		this.buddyAnonymizedId = buddyAnonymizedId;
	}

	public static Buddy createInstance(UUID buddyUserId, String nickname, Status sendingStatus, Status receivingStatus)
	{
		BuddyAnonymized buddyAnonymized = BuddyAnonymized.createInstance(sendingStatus, receivingStatus);
		buddyAnonymized = BuddyAnonymized.getRepository().save(buddyAnonymized);
		return new Buddy(UUID.randomUUID(), buddyUserId, nickname, buddyAnonymized.getId());
	}

	public UUID getBuddyAnonymizedId()
	{
		return buddyAnonymizedId;
	}

	public BuddyAnonymized getBuddyAnonymized()
	{
		return BuddyAnonymized.getRepository().findOne(buddyAnonymizedId);
	}

	public String getNickname()
	{
		return nickname;
	}

	public void setNickName(String nickname)
	{
		this.nickname = nickname;
	}

	public UUID getUserId()
	{
		return userId;
	}

	public User getUser()
	{
		return User.getRepository().findOne(userId);
	}

	public Optional<UUID> getUserAnonymizedId()
	{
		return getBuddyAnonymized().getUserAnonymizedId();
	}

	public Status getReceivingStatus()
	{
		return getBuddyAnonymized().getReceivingStatus();
	}

	public void setReceivingStatus(Status status)
	{
		getBuddyAnonymized().setReceivingStatus(status);
	}

	public void setSendingStatus(Status status)
	{
		getBuddyAnonymized().setSendingStatus(status);
	}

	public Status getSendingStatus()
	{
		return getBuddyAnonymized().getSendingStatus();
	}

	public LocalDateTime getLastStatusChangeTime()
	{
		return getBuddyAnonymized().getLastStatusChangeTime();
	}

	public void setUserAnonymizedId(UUID userAnonymizedId)
	{
		BuddyAnonymized buddyAnonymized = getBuddyAnonymized();
		buddyAnonymized.setUserAnonymizedId(userAnonymizedId);
		BuddyAnonymized.getRepository().save(buddyAnonymized);
	}

	public Buddy touch()
	{
		touchVersion++;
		return this;
	}
}
