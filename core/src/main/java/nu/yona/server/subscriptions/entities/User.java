/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "USERS")
public class User extends EntityWithUuid
{
	private int privateDataMigrationVersion;

	private String firstName;

	private String lastName;

	@Column(unique = true)
	private String mobileNumber;

	// YD-666 Change to LocalDate when all users have migrated their data
	private LocalDateTime roundedCreationDate;

	private LocalDate appLastOpenedDate;

	private byte[] initializationVector;

	private boolean isCreatedOnBuddyRequest;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private ConfirmationCode mobileNumberConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private ConfirmationCode overwriteUserConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private ConfirmationCode pinResetConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private UserPrivate userPrivate;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private NewDeviceRequest newDeviceRequest;

	@OneToOne(fetch = FetchType.LAZY)
	private MessageDestination messageDestination;

	private static int currentPrivateDataMigrationVersion;

	// Default constructor is required for JPA
	public User()
	{
		super(null);
	}

	public User(UUID id, byte[] initializationVector, LocalDate creationDate, String mobileNumber, UserPrivate userPrivate,
			MessageDestination messageDestination)
	{
		super(id);
		this.initializationVector = initializationVector;
		this.roundedCreationDate = creationDate.atStartOfDay();
		this.mobileNumber = mobileNumber;
		this.setUserPrivate(userPrivate);
		this.messageDestination = messageDestination;
		this.privateDataMigrationVersion = currentPrivateDataMigrationVersion;
	}

	public static UserRepository getRepository()
	{
		return (UserRepository) RepositoryProvider.getRepository(User.class, UUID.class);
	}

	public static void setCurrentPrivateDataMigrationVersion(int currentPrivateDataMigrationVersion)
	{
		User.currentPrivateDataMigrationVersion = currentPrivateDataMigrationVersion;
	}

	/**
	 * For testing purposes only.
	 */
	static int getCurrentPrivateDataMigrationVersion()
	{
		return currentPrivateDataMigrationVersion;
	}

	public LocalDateTime getCreationTime()
	{
		return userPrivate.getCreationTime();
	}

	public LocalDate getRoundedCreationDate()
	{
		return roundedCreationDate.toLocalDate();
	}

	public void setRoundedCreationDate(LocalDate roundedCreationDate)
	{
		this.roundedCreationDate = roundedCreationDate.atStartOfDay();
	}

	public void setCreationTime(LocalDateTime creationTime)
	{
		userPrivate.setCreationTime(creationTime);
		this.roundedCreationDate = creationTime.toLocalDate().atStartOfDay();
	}

	public Optional<LocalDate> getAppLastOpenedDate()
	{
		return Optional.ofNullable(appLastOpenedDate);
	}

	/**
	 * Don't call this, except from from UserDevice.
	 *
	 * @param appLastOpenedDate The date the app was opened last by this user. The date must be in the user's timezone.
	 */
	public void setAppLastOpenedDate(LocalDate appLastOpenedDate)
	{
		this.appLastOpenedDate = Objects.requireNonNull(appLastOpenedDate);
	}

	public boolean isCreatedOnBuddyRequest()
	{
		return isCreatedOnBuddyRequest;
	}

	public void unsetIsCreatedOnBuddyRequest()
	{
		isCreatedOnBuddyRequest = false;
	}

	public String getFirstName()
	{
		if (firstName != null)
		{
			// Name not moved to private user yet, so return it
			return firstName;
		}
		return getUserPrivate().getFirstName();
	}

	public void setFirstName(String firstName)
	{
		getUserPrivate().setFirstName(firstName);
	}

	public String getLastName()
	{
		if (lastName != null)
		{
			// Name not moved to private user yet, so return it
			return lastName;
		}
		return getUserPrivate().getLastName();
	}

	public void setLastName(String lastName)
	{
		getUserPrivate().setLastName(lastName);
	}

	public String getNickname()
	{
		return getUserPrivate().getNickname();
	}

	public void setNickname(String nickname)
	{
		getUserPrivate().setNickname(nickname);
	}

	public String getMobileNumber()
	{
		return mobileNumber;
	}

	public void setMobileNumber(String mobileNumber)
	{
		this.mobileNumber = mobileNumber;
	}

	public NewDeviceRequest getNewDeviceRequest()
	{
		return newDeviceRequest;
	}

	public void setNewDeviceRequest(NewDeviceRequest newDeviceRequest)
	{
		this.newDeviceRequest = Objects.requireNonNull(newDeviceRequest, "Use clearNewDeviceRequest to clear the request");
	}

	public void clearNewDeviceRequest()
	{
		newDeviceRequest = null;
	}

	public boolean isMobileNumberConfirmed()
	{
		return mobileNumberConfirmationCode == null;
	}

	public void markMobileNumberConfirmed()
	{
		this.mobileNumberConfirmationCode = null;
	}

	public void setMobileNumberConfirmationCode(ConfirmationCode mobileNumberConfirmationCode)
	{
		this.mobileNumberConfirmationCode = mobileNumberConfirmationCode;
	}

	public Optional<ConfirmationCode> getMobileNumberConfirmationCode()
	{
		return Optional.ofNullable(mobileNumberConfirmationCode);
	}

	private UserPrivate getUserPrivate()
	{
		CryptoSession.getCurrent().setInitializationVector(initializationVector);
		return userPrivate;
	}

	private void setUserPrivate(UserPrivate userPrivate)
	{
		this.userPrivate = userPrivate;
	}

	public Set<Goal> getGoals()
	{
		return getUserPrivate().getUserAnonymized().getGoals();
	}

	public Set<Goal> getGoalsIncludingHistoryItems()
	{
		return getUserPrivate().getUserAnonymized().getGoalsIncludingHistoryItems();
	}

	public UUID getUserAnonymizedId()
	{
		return getUserPrivate().getUserAnonymizedId();
	}

	public Optional<String> getAndClearVpnPassword()
	{
		return userPrivate.getAndClearVpnPassword();
	}

	public MessageDestination getNamedMessageDestination()
	{
		return messageDestination;
	}

	public void clearNamedMessageDestination()
	{
		messageDestination = null;
	}

	public void addBuddy(Buddy buddy)
	{
		getUserPrivate().addBuddy(buddy);
	}

	public void removeBuddy(Buddy buddy)
	{
		getUserPrivate().removeBuddy(buddy);
	}

	public UUID getNamedMessageSourceId()
	{
		return getUserPrivate().getNamedMessageSourceId();
	}

	public UUID getAnonymousMessageSourceId()
	{
		return getUserPrivate().getAnonymousMessageSourceId();
	}

	public Set<Buddy> getBuddies()
	{
		return getUserPrivate().getBuddies();
	}

	public boolean canAccessPrivateData()
	{
		if (!CryptoSession.isActive())
		{
			return false;
		}
		return getUserPrivate().isDecryptedProperly();
	}

	public UserAnonymized getAnonymized()
	{
		return getUserPrivate().getUserAnonymized();
	}

	public User touch()
	{
		getUserPrivate().touch();
		return this;
	}

	public void loadFully()
	{
		getUserPrivate();
	}

	public void setIsCreatedOnBuddyRequest()
	{
		isCreatedOnBuddyRequest = true;
	}

	public Buddy getBuddyByUserAnonymizedId(UUID relatedUserAnonymizedId)
	{
		return getBuddies().stream().filter(buddy -> buddy.getUserAnonymizedId().isPresent() && relatedUserAnonymizedId.equals(
				buddy.getUserAnonymizedId().get())).findAny().orElseThrow(
				() -> new IllegalStateException("Buddy for user anonymized ID " + relatedUserAnonymizedId + " not found"));
	}

	public void assertMobileNumberConfirmed()
	{
		if (!isMobileNumberConfirmed())
		{
			throw MobileNumberConfirmationException.notConfirmed(mobileNumber);
		}
	}

	public void setOverwriteUserConfirmationCode(ConfirmationCode overwriteUserConfirmationCode)
	{
		Objects.requireNonNull(overwriteUserConfirmationCode);
		this.overwriteUserConfirmationCode = overwriteUserConfirmationCode;
	}

	public Optional<ConfirmationCode> getOverwriteUserConfirmationCode()
	{
		return Optional.ofNullable(overwriteUserConfirmationCode);
	}

	public Optional<ConfirmationCode> getPinResetConfirmationCode()
	{
		return Optional.ofNullable(pinResetConfirmationCode);
	}

	public void setPinResetConfirmationCode(ConfirmationCode pinResetConfirmationCode)
	{
		this.pinResetConfirmationCode = pinResetConfirmationCode;
	}

	public Set<Buddy> getBuddiesRelatedToRemovedUsers()
	{
		return getUserPrivate().getBuddiesRelatedToRemovedUsers();
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return getUserPrivate().getLastMonitoredActivityDate();
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		getUserPrivate().setLastMonitoredActivityDate(lastMonitoredActivityDate);
	}

	public int getPrivateDataMigrationVersion()
	{
		return privateDataMigrationVersion;
	}

	public void setPrivateDataMigrationVersion(int privateDataMigrationVersion)
	{
		this.privateDataMigrationVersion = privateDataMigrationVersion;
	}

	public Optional<UUID> getUserPhotoId()
	{
		return getUserPrivate().getUserPhotoId();
	}

	public void setUserPhotoId(Optional<UUID> userPhotoId)
	{
		getUserPrivate().setUserPhotoId(userPhotoId);
	}

	public Set<UserDevice> getDevices()
	{
		return getUserPrivate().getDevices();
	}

	public void addDevice(UserDevice device)
	{
		getUserPrivate().addDevice(device);
	}

	public void removeDevice(UserDevice device)
	{
		getUserPrivate().removeDevice(device);
	}

	public void moveFirstAndLastNameToPrivate()
	{
		if (firstName == null)
		{
			// Apparently already moved
			return;
		}
		userPrivate.setFirstName(firstName);
		userPrivate.setLastName(lastName);
		firstName = null;
		lastName = null;
	}

	public void moveCreationTimeToPrivate()
	{
		if (userPrivate.getCreationTime() != null)
		{
			// Apparently already moved
			return;
		}
		userPrivate.setCreationTime(roundedCreationDate);

		// Round it to a date. Further rounding might happen as part of the app open event
		roundedCreationDate = roundedCreationDate.toLocalDate().atStartOfDay();
	}

	public LocalDate getDateInUserTimezone(LocalDateTime utcDateTime)
	{
		return TimeUtil.toUtcZonedDateTime(utcDateTime).withZoneSameInstant(getAnonymized().getTimeZone()).toLocalDate();
	}
}
