/*******************************************************************************
 * Copyright (c) 2015, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.springframework.core.annotation.Order;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.DateTimeFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;
import nu.yona.server.device.entities.BuddyDevice;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.service.UserServiceException;
import nu.yona.server.subscriptions.service.migration.EncryptFirstAndLastName;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "BUDDIES")
public class Buddy extends PrivateUserProperties
{
	private static final int VERSION_OF_NAME_MIGRATION = EncryptFirstAndLastName.class.getAnnotation(Order.class).value() + 1;

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
	 * The BuddyDevice entities owned by this user
	 */
	@BatchSize(size = 20)
	@OneToMany(mappedBy = "owningBuddy", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<BuddyDevice> devices;

	// Default constructor is required for JPA
	public Buddy()
	{
		super();
	}

	private Buddy(UUID id, UUID userId, String firstName, String lastName, String nickname, Optional<UUID> userPhotoId,
			UUID buddyAnonymizedId)
	{
		super(id, firstName, lastName, Objects.requireNonNull(nickname), userPhotoId);
		this.userId = Objects.requireNonNull(userId);
		this.buddyAnonymizedId = Objects.requireNonNull(buddyAnonymizedId);
		this.devices = new HashSet<>();

		setLastStatusChangeTimeToNow();
	}

	public static BuddyRepository getRepository()
	{
		return (BuddyRepository) RepositoryProvider.getRepository(Buddy.class, UUID.class);
	}

	public static Buddy createInstance(UUID buddyUserId, String firstName, String lastName, String nickname,
			Optional<UUID> userPhotoId, Status sendingStatus, Status receivingStatus)
	{
		BuddyAnonymized buddyAnonymized = BuddyAnonymized.createInstance(sendingStatus, receivingStatus);
		buddyAnonymized = BuddyAnonymized.getRepository().save(buddyAnonymized);
		return new Buddy(UUID.randomUUID(), buddyUserId, firstName, lastName, nickname, userPhotoId, buddyAnonymized.getId());
	}

	public UUID getOwningUserPrivateId()
	{
		return owningUserPrivateId;
	}

	public UUID getBuddyAnonymizedId()
	{
		return buddyAnonymizedId;
	}

	public BuddyAnonymized getBuddyAnonymized()
	{
		return BuddyAnonymized.getRepository().findById(buddyAnonymizedId)
				.orElseThrow(() -> InvalidDataException.missingEntity(BuddyAnonymized.class, buddyAnonymizedId));
	}

	public UUID getUserId()
	{
		return userId;
	}

	public User getUser()
	{
		return getUserIfExisting().orElseThrow(() -> UserServiceException.notFoundById(userId));
	}

	public Optional<User> getUserIfExisting()
	{
		return User.getRepository().findById(userId);
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

	/**
	 * Determines the first name (see {@link #determineName(Supplier, Optional, Function, String, String)}).
	 *
	 * @param user Optional user
	 * @return The first name or a substitute for it (never null)
	 */
	public String determineFirstName(Optional<User> user)
	{
		return determineName(this::getFirstName, user, User::getFirstName, "message.alternative.first.name", getNickname());
	}

	/**
	 * Determines the last name (see {@link #determineName(Supplier, Optional, Function, String, String)}).
	 *
	 * @param user Optional user
	 * @return The last name or a substitute for it (never null)
	 */
	public String determineLastName(Optional<User> user)
	{
		return determineName(this::getLastName, user, User::getLastName, "message.alternative.last.name", getNickname());
	}

	/**
	 * Determines the name of the user (first or last) through an algorithm that ensures the best possible value is returned, but
	 * never null. It first tries calling the getter that is supposed to take it from the buddy entity or message. If that returns
	 * null and a user is given and that user is not yet migrated (i.e. the name is not removed from it), it tries to get that. If
	 * that doesn't return anything either, it builds a string based on the given nickname.
	 *
	 * @param buddyUserNameGetter Getter to fetch the name from the buddy entity or a message
	 * @param user                Optional user entity
	 * @param userNameGetter      Getter to fetch the name (first or last) from the user entity
	 * @param fallbackMessageId   The ID of the translatable message to build the fallback string
	 * @param nickname            The nickname to include in the fallback string
	 * @return The name or a substitute for it (never null)
	 */
	public static String determineName(Supplier<String> buddyUserNameGetter, Optional<User> user,
			Function<User, String> userNameGetter, String fallbackMessageId, String nickname)
	{
		String name = buddyUserNameGetter.get();
		if (name != null)
		{
			return name;
		}
		if ((user.isPresent()) && (user.get().getPrivateDataMigrationVersion() < VERSION_OF_NAME_MIGRATION))
		{
			// User is not deleted yet and not yet migrated, so get the name from the user entity
			name = userNameGetter.apply(user.get());
		}
		if (name != null)
		{
			return name;
		}
		// We're apparently in a migration process to move first and last name to the private data
		// The app will fetch the message, causing processing of all unprocessed messages. That'll fill in the first and last
		// name in the buddy entity, so from then onward, the user will see the right data
		return Translator.getInstance().getLocalizedMessage(fallbackMessageId, nickname);
	}

	public boolean isAccepted()
	{
		return (getReceivingStatus() == Status.ACCEPTED) || (getSendingStatus() == Status.ACCEPTED);
	}
}
