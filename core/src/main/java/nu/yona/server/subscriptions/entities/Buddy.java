/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.seckey.DateTimeFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;
import nu.yona.server.device.entities.BuddyDevice;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "BUDDIES")
public class Buddy extends PrivateUserProperties
{
	@Type(type = "uuid-char")
	@Column(name = "owning_user_private_id")
	private UUID owningUserPrivateId;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID userId;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID buddyAnonymizedId;

	@Convert(converter = DateTimeFieldEncryptor.class)
	private LocalDateTime lastStatusChangeTime;

	/**
	 * The BuddyAnonymized entities owned by this user
	 */
	@OneToMany(mappedBy = "owningBuddy", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<BuddyDevice> devices;

	// Default constructor is required for JPA
	public Buddy()
	{
		super();
	}

	private Buddy(UUID id, UUID userId, String firstName, String lastName, String nickname, UUID buddyAnonymizedId)
	{
		super(id, firstName, lastName, Objects.requireNonNull(nickname));
		this.userId = Objects.requireNonNull(userId);
		this.buddyAnonymizedId = Objects.requireNonNull(buddyAnonymizedId);
		this.devices = new HashSet<>();

		setLastStatusChangeTimeToNow();
	}

	public static BuddyRepository getRepository()
	{
		return (BuddyRepository) RepositoryProvider.getRepository(Buddy.class, UUID.class);
	}

	public static Buddy createInstance(UUID buddyUserId, String firstName, String lastName, String nickname, Status sendingStatus,
			Status receivingStatus)
	{
		BuddyAnonymized buddyAnonymized = BuddyAnonymized.createInstance(sendingStatus, receivingStatus);
		buddyAnonymized = BuddyAnonymized.getRepository().save(buddyAnonymized);
		return new Buddy(UUID.randomUUID(), buddyUserId, firstName, lastName, nickname, buddyAnonymized.getId());
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

	public void addDevice(BuddyDevice device)
	{
		devices.add(device);
		device.setOwningBuddy(this);
	}

	public void removeDevice(BuddyDevice device)
	{
		boolean removed = devices.remove(device);
		assert removed;
		device.clearOwningBuddy();
	}

	public Set<BuddyDevice> getDevices()
	{
		return devices;
	}
}
