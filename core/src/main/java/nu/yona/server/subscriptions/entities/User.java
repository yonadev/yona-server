/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Collections;
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
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageSource;

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

	private byte[] initializationVector;

	private boolean createdOnBuddyRequest;

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

	private User(UUID id, byte[] initializationVector, boolean createdOnBuddyRequest, String firstName, String lastName,
			String mobileNumber, UserPrivate userPrivate, MessageDestination messageDestination)
	{
		super(id);
		this.initializationVector = initializationVector;
		this.createdOnBuddyRequest = createdOnBuddyRequest;
		this.firstName = firstName;
		this.lastName = lastName;
		this.mobileNumber = mobileNumber;
		this.setUserPrivate(userPrivate);
		this.messageDestination = messageDestination;
	}

	public static User createInstance(String firstName, String lastName, String nickName, String mobileNumber,
			Set<String> deviceNames, Set<Goal> goals)
	{
		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		UserPrivate userPrivate = UserPrivate.createInstance(nickName, deviceNames, goals, anonymousMessageSource,
				namedMessageSource);
		return new User(UUID.randomUUID(), initializationVector, false, firstName, lastName, mobileNumber, userPrivate,
				namedMessageSource.getDestination());
	}

	public static User createInstanceOnBuddyRequest(String firstName, String lastName, String nickName, String mobileNumber)
	{
		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		Set<Goal> goals = Collections.emptySet();
		Set<String> devices = Collections.emptySet();

		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		UserPrivate userPrivate = UserPrivate.createInstance(nickName, devices, goals, anonymousMessageSource,
				namedMessageSource);
		return new User(UUID.randomUUID(), initializationVector, true, firstName, lastName, mobileNumber, userPrivate,
				namedMessageSource.getDestination());
	}

	public boolean isCreatedOnBuddyRequest()
	{
		return createdOnBuddyRequest;
	}

	public void removeIsCreatedOnBuddyRequest()
	{
		createdOnBuddyRequest = false;
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

	public String getNickName()
	{
		return getUserPrivate().getNickname();
	}

	public void setNickName(String nickName)
	{
		getUserPrivate().setNickname(nickName);
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

	private UserPrivate getUserPrivate()
	{
		CryptoSession.getCurrent().setInitializationVector(initializationVector);
		return userPrivate;
	}

	private void setUserPrivate(UserPrivate userPrivate)
	{
		this.userPrivate = userPrivate;
	}

	public void setNickname(String nickName)
	{
		getUserPrivate().setNickname(nickName);
	}

	public Set<String> getDeviceNames()
	{
		return getUserPrivate().getDeviceNames();
	}

	public void setDeviceNames(Set<String> deviceNames)
	{
		getUserPrivate().setDeviceNames(deviceNames);
	}

	public Set<Goal> getGoals()
	{
		return getUserPrivate().getUserAnonymized().getGoals();
	}

	public UUID getLoginID()
	{
		return getUserPrivate().getLoginID();
	}

	public MessageDestination getNamedMessageDestination()
	{
		return messageDestination;
	}

	public void addBuddy(Buddy buddy)
	{
		getUserPrivate().addBuddy(buddy);
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
}
