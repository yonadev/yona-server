/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.InvalidDataException;

/**
 * This class captures the anonymized information of a buddy. <br/>
 * NOTE: The data of a BuddyAnonymized is cached as part of UserAnonymized, so for any update to a BuddyAnonymized,
 * UserAnonymizedService.updateUserAnonymized must be called.
 */
@Entity
@Table(name = "BUDDIES_ANONYMIZED")
public class BuddyAnonymized extends EntityWithUuid
{
	public enum Status
	{
		NOT_REQUESTED, REQUESTED, ACCEPTED, REJECTED
	}

	/**
	 * The anonymized user owning this buddy anonymized entity.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private UserAnonymized owningUserAnonymized;

	/**
	 * The anonymized user of the buddy. This is kept as UUID rather than an entity, as the ID is still needed even if the entity
	 * has been deleted, to clean up the messages sent by this user.
	 */
	@JdbcTypeCode(java.sql.Types.VARCHAR)
	@Column(name = "user_anonymized_id")
	private UUID userAnonymizedId;

	/*
	 * When the sendingStatus is ACCEPTED, the buddy user accepted to send goal conflicts and activity information to the other
	 * side.
	 */
	@JdbcType(value = IntegerJdbcType.class)
	private Status sendingStatus = Status.NOT_REQUESTED;

	/*
	 * When the receivingStatus is ACCEPTED, the buddy user accepted to receive goal conflicts and activity information from the
	 * other side.
	 */
	@JdbcType(value = IntegerJdbcType.class)
	private Status receivingStatus = Status.NOT_REQUESTED;

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
	}

	public static BuddyAnonymizedRepository getRepository()
	{
		return (BuddyAnonymizedRepository) RepositoryProvider.getRepository(BuddyAnonymized.class, UUID.class);
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
		return UserAnonymized.getRepository().findById(userAnonymizedId)
				.orElseThrow(() -> InvalidDataException.userAnonymizedIdNotFound(userAnonymizedId));
	}

	public Status getSendingStatus()
	{
		return sendingStatus;
	}

	/**
	 * Do not call this method directly, it should be called from the Buddy entity to update the last status change time.
	 *
	 * @param sendingStatus
	 */
	public void setSendingStatus(Status sendingStatus)
	{
		this.sendingStatus = sendingStatus;
	}

	public Status getReceivingStatus()
	{
		return receivingStatus;
	}

	/**
	 * Do not call this method directly, it should be called from the Buddy entity to update the last status change time.
	 *
	 * @param sendingStatus
	 */
	public void setReceivingStatus(Status receivingStatus)
	{
		this.receivingStatus = receivingStatus;
	}

	public void setOwningUserAnonymized(UserAnonymized owningUserAnonymized)
	{
		this.owningUserAnonymized = owningUserAnonymized;
	}

	public void clearOwningUserAnonymized()
	{
		this.owningUserAnonymized = null;
	}

	public Optional<UUID> getUserAnonymizedId()
	{
		return Optional.ofNullable(userAnonymizedId);
	}

	public void setUserAnonymizedId(UUID userAnonymizedId)
	{
		this.userAnonymizedId = Objects.requireNonNull(userAnonymizedId);
	}

	public void setDisconnected()
	{
		this.sendingStatus = Status.REJECTED;
		this.receivingStatus = Status.REJECTED;
		this.userAnonymizedId = null;
	}
}
