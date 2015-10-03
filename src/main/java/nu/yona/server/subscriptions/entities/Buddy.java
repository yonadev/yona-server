/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.LongFieldEncrypter;
import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.crypto.UUIDFieldEncrypter;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.model.RepositoryProvider;

@Entity
@Table(name = "BUDDIES")
public class Buddy {
	public static BuddyRepository getRepository() {
		return (BuddyRepository) RepositoryProvider.getRepository(Buddy.class, Long.class);
	}

	public enum Status {
		NOT_REQUESTED, REQUESTED, ACCEPTED, REJECTED
	};

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Convert(converter = LongFieldEncrypter.class)
	long userID;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID accessorID;

	@Convert(converter = LongFieldEncrypter.class)
	private long destinationID;

	@Convert(converter = StringFieldEncrypter.class)
	private String nickName;

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = UUIDFieldEncrypter.class)
	private Set<UUID> goalIDs;

	@Transient
	private Set<Goal> goals = new HashSet<>();

	private Status sendingStatus = Status.NOT_REQUESTED;
	private Status receivingStatus = Status.NOT_REQUESTED;

	public Buddy() {
		// Default constructor is required for JPA
	}

	private Buddy(long userID, String nickName) {
		this.userID = userID;
		this.nickName = nickName;
	}

	public static Buddy createInstance(User user, String nickName) {
		return new Buddy(user.getID(), nickName);
	}

	public long getID() {
		return id;
	}

	public UUID getAccessorID() {
		return accessorID;
	}

	public void setAccessorID(UUID accessorID) {
		this.accessorID = accessorID;
	}

	public long getDestinationID() {
		return destinationID;
	}

	public void setDestinationID(long destinationID) {
		this.destinationID = destinationID;
	}

	public String getNickName() {
		return nickName;
	}

	public void setNickName(String nickName) {
		this.nickName = nickName;
	}

	public User getUser() {
		return User.getRepository().findOne(userID);
	}

	public void setGoalNames(Set<String> goalNames) {
		goals = goalNames.stream().map(Goal.getRepository()::findByName).collect(Collectors.toSet());
	}

	public Status getSendingStatus() {
		return sendingStatus;
	}

	public void setSendingStatus(Status sendingStatus) {
		this.sendingStatus = sendingStatus;
	}

	public Status getReceivingStatus() {
		return receivingStatus;
	}

	public void setReceivingStatus(Status receivingStatus) {
		this.receivingStatus = receivingStatus;
	}
}
