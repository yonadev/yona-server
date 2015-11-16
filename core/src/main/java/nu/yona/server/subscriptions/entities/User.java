/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
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

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageSource;

@Entity
@Table(name = "USERS")
public class User extends EntityWithID {
	public static UserRepository getRepository() {
		return (UserRepository) RepositoryProvider.getRepository(User.class, UUID.class);
	}

	private String firstName;

	private String lastName;

	@Column(unique = true)
	private String emailAddress;

	@Column(unique = true)
	private String mobileNumber;

	private boolean createdOnBuddyRequest;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private UserPrivate userPrivate;

	@OneToOne
	private MessageDestination messageDestination;

	// Default constructor is required for JPA
	public User() {
		super(null);
	}

	private User(UUID id, boolean createdOnBuddyRequest, String firstName, String lastName, String emailAddress,
			String mobileNumber, UserPrivate userPrivate, MessageDestination messageDestination) {
		super(id);
		this.createdOnBuddyRequest = createdOnBuddyRequest;
		this.firstName = firstName;
		this.lastName = lastName;
		this.emailAddress = emailAddress;
		this.mobileNumber = mobileNumber;
		this.userPrivate = userPrivate;
		this.messageDestination = messageDestination;
	}

	public static User createInstance(String firstName, String lastName, String nickName, String emailAddress,
			String mobileNumber, Set<String> deviceNames, Set<Goal> goals) {
		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		Accessor accessor = Accessor.createInstance(goals);
		accessor.addDestination(anonymousMessageSource.getDestination());
		Accessor.getRepository().save(accessor);
		UserPrivate userPrivate = UserPrivate.createInstance(nickName, accessor, deviceNames, goals,
				anonymousMessageSource, namedMessageSource);
		return new User(UUID.randomUUID(), false, firstName, lastName, emailAddress, mobileNumber, userPrivate,
				namedMessageSource.getDestination());
	}

	public static User createInstanceOnBuddyRequest(String firstName, String lastName, String nickName,
			String emailAddress, String mobileNumber) {
		Set<Goal> goals = Collections.emptySet();
		Set<String> devices = Collections.emptySet();

		MessageSource anonymousMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		MessageSource namedMessageSource = MessageSource.getRepository().save(MessageSource.createInstance());
		Accessor accessor = Accessor.createInstance(goals);
		accessor.addDestination(anonymousMessageSource.getDestination());
		Accessor.getRepository().save(accessor);
		UserPrivate userPrivate = UserPrivate.createInstance(nickName, accessor, devices, goals,
				anonymousMessageSource, namedMessageSource);
		return new User(UUID.randomUUID(), true, firstName, lastName, emailAddress, mobileNumber, userPrivate,
				namedMessageSource.getDestination());
	}

	public boolean isCreatedOnBuddyRequest() {
		return createdOnBuddyRequest;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getNickName() {
		return userPrivate.getNickname();
	}

	public void setNickName(String nickName) {
		userPrivate.setNickname(nickName);
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	public void setMobileNumber(String mobileNumber) {
		this.mobileNumber = mobileNumber;
	}

	public Set<String> getDeviceNames() {
		return userPrivate.getDeviceNames();
	}

	public void setDeviceNames(Set<String> deviceNames) {
		userPrivate.setDeviceNames(deviceNames);
	}

	public Set<Goal> getGoals() {
		return userPrivate.getGoals();
	}

	public UUID getAccessorID() {
		return userPrivate.getAccessorID();
	}

	public MessageDestination getNamedMessageDestination() {
		return messageDestination;
	}

	public void addBuddy(Buddy buddy) {
		userPrivate.addBuddy(buddy);
	}

	public MessageSource getNamedMessageSource() {
		return userPrivate.getNamedMessageSource();
	}

	public MessageSource getAnonymousMessageSource() {
		return userPrivate.getAnonymousMessageSource();
	}

	public MessageDestination getAnonymousMessageDestination() {
		return getAnonymousMessageSource().getDestination();
	}

	public Set<Buddy> getBuddies() {
		return userPrivate.getBuddies();
	}

	public boolean canAccessPrivateData() {
		return userPrivate.isDecryptedProperly();
	}

	public VPNProfile getVPNProfile() {
		return userPrivate.getVPNProfile();
	}
}
