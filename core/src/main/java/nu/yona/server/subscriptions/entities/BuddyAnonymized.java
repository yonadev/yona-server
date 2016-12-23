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
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "BUDDIES_ANONYMIZED")
public class BuddyAnonymized extends EntityWithUuid
{
	public enum Status
	{
		NOT_REQUESTED, REQUESTED, ACCEPTED, REJECTED
	}

	public static BuddyAnonymizedRepository getRepository()
	{
		return (BuddyAnonymizedRepository) RepositoryProvider.getRepository(BuddyAnonymized.class, UUID.class);
	}

	@Type(type = "uuid-char")
	@Column(name = "owning_user_anonymized_id")
	private UUID owningUserAnonymizedId;

	@Type(type = "uuid-char")
	private UUID userAnonymizedId;

	/*
	 * When the sendingStatus is ACCEPTED, the buddy user accepted to send goal conflicts and activity information to the other
	 * side.
	 */
	private Status sendingStatus = Status.NOT_REQUESTED;

	/*
	 * When the receivingStatus is ACCEPTED, the buddy user accepted to receive goal conflicts and activity information from the
	 * other side.
	 */
	private Status receivingStatus = Status.NOT_REQUESTED;

	private LocalDateTime lastStatusChangeTime;

	// Default constructor is required for JPA
	public BuddyAnonymized()
	{
		super(null);
	}

	private BuddyAnonymized(UUID id, Status sendingStatus, Status receivingStatus)
	{
		super(id);
		this.sendingStatus = sendingStatus;
		this.receivingStatus = receivingStatus;
		setLastStatusChangeTimeToNow();
	}

	public static BuddyAnonymized createInstance(Status sendingStatus, Status receivingStatus)
	{
		return new BuddyAnonymized(UUID.randomUUID(), sendingStatus, receivingStatus);
	}

	public UserAnonymized getUserAnonymized()
	{
		if (userAnonymizedId == null)
		{
			throw new IllegalStateException("UserAnonymized is not available in this state");
		}
		return UserAnonymized.getRepository().findOne(userAnonymizedId);
	}

	public Status getSendingStatus()
	{
		return sendingStatus;
	}

	public void setSendingStatus(Status sendingStatus)
	{
		if (this.sendingStatus == sendingStatus)
		{
			return;
		}
		this.sendingStatus = sendingStatus;
		setLastStatusChangeTimeToNow();
	}

	public Status getReceivingStatus()
	{
		return receivingStatus;
	}

	public void setReceivingStatus(Status receivingStatus)
	{
		if (this.receivingStatus == receivingStatus)
		{
			return;
		}
		this.receivingStatus = receivingStatus;
		setLastStatusChangeTimeToNow();
	}

	public Optional<UUID> getUserAnonymizedId()
	{
		return Optional.ofNullable(userAnonymizedId);
	}

	public void setUserAnonymizedId(UUID userAnonymizedId)
	{
		Objects.requireNonNull(userAnonymizedId);
		this.userAnonymizedId = userAnonymizedId;
	}

	public void setDisconnected()
	{
		this.sendingStatus = Status.REJECTED;
		this.receivingStatus = Status.REJECTED;
		this.userAnonymizedId = null;
		setLastStatusChangeTimeToNow();
	}

	public LocalDateTime getLastStatusChangeTime()
	{
		return lastStatusChangeTime;
	}

	private void setLastStatusChangeTimeToNow()
	{
		lastStatusChangeTime = TimeUtil.utcNow();
	}
}
