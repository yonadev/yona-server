/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.MobileNumberConfirmationException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "USERS")
public class User extends EntityWithID
{
	public static UserRepository getRepository()
	{
		return (UserRepository) RepositoryProvider.getRepository(User.class, UUID.class);
	}

	private String firstName;

	private String lastName;

	@Column(unique = true)
	private String mobileNumber;

	private LocalDateTime creationTime;

	private byte[] initializationVector;

	private boolean isCreatedOnBuddyRequest;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private ConfirmationCode mobileNumberConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private ConfirmationCode overwriteUserConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	private ConfirmationCode pinResetConfirmationCode;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
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

	private User(UUID id, byte[] initializationVector, String firstName, String lastName, String mobileNumber,
			UserPrivate userPrivate, MessageDestination messageDestination)
	{
		super(id);
		this.initializationVector = initializationVector;
		this.firstName = firstName;
		this.lastName = lastName;
		this.mobileNumber = mobileNumber;
		this.setUserPrivate(userPrivate);
		this.messageDestination = messageDestination;
		this.creationTime = TimeUtil.utcNow();
	}

	public static User createInstance(String firstName, String lastName, String nickname, String mobileNumber, String vpnPassword,
			Set<Goal> goals)
	{
		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		UserPrivate userPrivate = UserPrivate.createInstance(nickname, vpnPassword, goals, anonymousMessageSource,
				namedMessageSource);
		return new User(UUID.randomUUID(), initializationVector, firstName, lastName, mobileNumber, userPrivate,
				namedMessageSource.getDestination());
	}

	public LocalDateTime getCreationTime()
	{
		return this.creationTime;
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

	public UUID getUserAnonymizedID()
	{
		return getUserPrivate().getUserAnonymizedID();
	}

	public String getVPNPassword()
	{
		return getUserPrivate().getVPNPassword();
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

	public void removeBuddiesFromUser(UUID fromUserID)
	{
		getUserPrivate().removeBuddyForUserID(fromUserID);
	}

	public MessageSource getNamedMessageSource()
	{
		return getUserPrivate().getNamedMessageSource();
	}

	public MessageSource getAnonymousMessageSource()
	{
		return getUserPrivate().getAnonymousMessageSource();
	}

	public MessageDestination getAnonymousMessageDestination()
	{
		return getAnonymousMessageSource().getDestination();
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

	public Buddy getBuddyByUserAnonymizedID(UUID relatedUserAnonymizedID)
	{
		return getBuddies().stream().filter(buddy -> buddy.getUserAnonymizedID().isPresent()
				&& relatedUserAnonymizedID.equals(buddy.getUserAnonymizedID().get())).findAny().get();
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

	public UUID getVPNLoginID()
	{
		return getUserPrivate().getVPNLoginID();
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
		return userPrivate.getBuddiesRelatedToRemovedUsers();
	}
}
