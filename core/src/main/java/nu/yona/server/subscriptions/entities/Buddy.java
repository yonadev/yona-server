/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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

import nu.yona.server.crypto.seckey.DateTimeFieldEncryptor;
import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "BUDDIES")
public class Buddy extends EntityWithUuidAndTouchVersion
{
	@Type(type = "uuid-char")
	@Column(name = "owning_user_private_id")
	private UUID owningUserPrivateId;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID userId;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID buddyAnonymizedId;

	@Convert(converter = StringFieldEncryptor.class)
	private String nickname;

	@Convert(converter = DateTimeFieldEncryptor.class)
	private LocalDateTime lastStatusChangeTime;

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

		setLastStatusChangeTimeToNow();
	}

	public static BuddyRepository getRepository()
	{
		return (BuddyRepository) RepositoryProvider.getRepository(Buddy.class, UUID.class);
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
		BuddyAnonymized buddyAnonymized = BuddyAnonymized.getRepository().findOne(buddyAnonymizedId);
		assert buddyAnonymized != null : "Buddy with ID " + getId() + " cannot find buddy anonymized with ID "
				+ buddyAnonymizedId;

		return buddyAnonymized;
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
		BuddyAnonymized buddyAnonymized = getBuddyAnonymized();
		buddyAnonymized.setReceivingStatus(status);
		BuddyAnonymized.getRepository().save(buddyAnonymized);

		setLastStatusChangeTimeToNow();
	}

	public void setSendingStatus(Status status)
	{
		BuddyAnonymized buddyAnonymized = getBuddyAnonymized();
		buddyAnonymized.setSendingStatus(status);
		BuddyAnonymized.getRepository().save(buddyAnonymized);

		setLastStatusChangeTimeToNow();
	}

	public Status getSendingStatus()
	{
		return getBuddyAnonymized().getSendingStatus();
	}

	public LocalDateTime getLastStatusChangeTime()
	{
		return lastStatusChangeTime;
	}

	private void setLastStatusChangeTimeToNow()
	{
		lastStatusChangeTime = TimeUtil.utcNow();
	}

	public void setUserAnonymizedId(UUID userAnonymizedId)
	{
		BuddyAnonymized buddyAnonymized = getBuddyAnonymized();
		buddyAnonymized.setUserAnonymizedId(userAnonymizedId);
		BuddyAnonymized.getRepository().save(buddyAnonymized);
	}

	@Override
	public Buddy touch()
	{
		super.touch();
		return this;
	}

	/**
	 * @deprecated only for use by migration step.
	 */
	@Deprecated
	public void setLastStatusChangeTime(LocalDateTime lastStatusChangeTime)
	{
		this.lastStatusChangeTime = lastStatusChangeTime;
	}
}
