/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "USERS")
public class User extends EntityWithUuid
{
	public static UserRepository getRepository()
	{
		return (UserRepository) RepositoryProvider.getRepository(User.class, UUID.class);
	}

	private int privateDataMigrationVersion;

	private String firstName;

	private String lastName;

	@Column(unique = true)
	private String mobileNumber;

	private LocalDateTime creationTime;

	private LocalDate appLastOpenedDate;

	private byte[] initializationVector;

	private boolean isCreatedOnBuddyRequest;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
	private ConfirmationCode mobileNumberConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
	private ConfirmationCode overwriteUserConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
	private ConfirmationCode pinResetConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private UserPrivate userPrivate;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private NewDeviceRequest newDeviceRequest;

	@OneToOne
	private MessageDestination messageDestination;

	// Default constructor is required for JPA
	public User()
	{
		super(null);
	}

	public User(UUID id, byte[] initializationVector, String firstName, String lastName, String mobileNumber,
			UserPrivate userPrivate, MessageDestination messageDestination)
	{
		super(id);
		this.initializationVector = initializationVector;
		this.creationTime = TimeUtil.utcNow();
		this.appLastOpenedDate = this.creationTime.toLocalDate();
		this.firstName = firstName;
		this.lastName = lastName;
		this.mobileNumber = mobileNumber;
		this.setUserPrivate(userPrivate);
		this.messageDestination = messageDestination;
		this.privateDataMigrationVersion = PrivateUserDataMigrationService.getCurrentVersion();
	}

	public LocalDateTime getCreationTime()
	{
		return this.creationTime;
	}

	public LocalDate getAppLastOpenedDate()
	{
		return appLastOpenedDate;
	}

	public void setAppLastOpenedDate(LocalDate appLastOpenedDate)
	{
		Objects.requireNonNull(appLastOpenedDate);
		this.appLastOpenedDate = appLastOpenedDate;
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
		return firstName;
	}

	public void setFirstName(String firstName)
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public void setLastName(String lastName)
	{
		this.lastName = lastName;
	}

	public String getNickname()
	{
		return getUserPrivate().getNickname();
	}

	public void setNickName(String nickname)
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
		this.newDeviceRequest = newDeviceRequest;
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

	public ConfirmationCode getMobileNumberConfirmationCode()
	{
		return mobileNumberConfirmationCode;
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

	public void setNickname(String nickname)
	{
		getUserPrivate().setNickname(nickname);
	}

	public Set<Goal> getGoals()
	{
		return getUserPrivate().getUserAnonymized().getGoals();
	}

	public UUID getUserAnonymizedId()
	{
		return getUserPrivate().getUserAnonymizedId();
	}

	public String getVpnPassword()
	{
		return getUserPrivate().getVpnPassword();
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

	public void removeBuddiesFromUser(UUID fromUserId)
	{
		getUserPrivate().removeBuddyForUserId(fromUserId);
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
		return getUserPrivate().isDecryptedProperly();
	}

	public UserAnonymized getAnonymized()
	{
		return getUserPrivate().getUserAnonymized();
	}

	public void touch()
	{
		getUserPrivate().touch();
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
		return getBuddies().stream().filter(buddy -> buddy.getUserAnonymizedId().isPresent()
				&& relatedUserAnonymizedId.equals(buddy.getUserAnonymizedId().get())).findAny().get();
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
		this.overwriteUserConfirmationCode = overwriteUserConfirmationCode;
	}

	public ConfirmationCode getOverwriteUserConfirmationCode()
	{
		return overwriteUserConfirmationCode;
	}

	public UUID getVpnLoginId()
	{
		return getUserPrivate().getVpnLoginId();
	}

	public ConfirmationCode getPinResetConfirmationCode()
	{
		return pinResetConfirmationCode;
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
}
