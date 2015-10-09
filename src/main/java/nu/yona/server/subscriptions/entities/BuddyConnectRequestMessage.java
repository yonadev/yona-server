/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.subscriptions.entities.Buddy.Status;

@Entity
public class BuddyConnectRequestMessage extends Message {

	@Transient
	private UUID requestingUserID;
	private byte[] requestingUserIDCiphertext;

	@Transient
	private UUID accessorID;
	private byte[] accessorIDCiphertext;

	@Transient
	private Set<UUID> goalIDs;
	private byte[] goalIDsCiphertext;

	@Transient
	private String nickname;
	private byte[] nicknameCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private UUID buddyID;
	private byte[] buddyIDCiphertext;

	private Status status = Status.NOT_REQUESTED;

	public BuddyConnectRequestMessage() {
		super(null);
		// Default constructor is required for JPA
	}

	private BuddyConnectRequestMessage(UUID id, UUID requestingUserID, UUID accessorID, Set<UUID> goalIDs,
			String nickname, String message, UUID buddyID) {
		super(id);
		this.buddyID = buddyID;
		if (requestingUserID == null) {
			throw new IllegalArgumentException("requestingUserID cannot be null");
		}
		if (accessorID == null) {
			throw new IllegalArgumentException("accessorID cannot be null");
		}
		this.requestingUserID = requestingUserID;
		this.accessorID = accessorID;
		this.goalIDs = goalIDs;
		this.nickname = nickname;
		this.message = message;
	}

	public User getRequestingUser() {
		return User.getRepository().findOne(requestingUserID);
	}

	public UUID getAccessorID() {
		if (accessorID == null) {
			throw new IllegalStateException("Message has not been decrypted");
		}
		return accessorID;
	}

	public Set<Goal> getGoals() {
		return goalIDs.stream().map(id -> Goal.getRepository().findOne(id)).collect(Collectors.toSet());
	}

	public String getNickname() {
		return nickname;
	}

	public String getMessage() {
		return message;
	}

	public UUID getBuddyID() {
		return buddyID;
	}

	public boolean isAccepted() {
		return status == Status.ACCEPTED;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public static BuddyConnectRequestMessage createInstance(User requestingUser, Set<Goal> goals, String nickname,
			String message, UUID buddyID) {
		return new BuddyConnectRequestMessage(UUID.randomUUID(), requestingUser.getID(), requestingUser.getAccessorID(),
				goals.stream().map(g -> g.getID()).collect(Collectors.toSet()), nickname, message, buddyID);
	}

	@Override
	public void encrypt(Encryptor encryptor) {
		requestingUserIDCiphertext = encryptor.encrypt(requestingUserID);
		accessorIDCiphertext = encryptor.encrypt(accessorID);
		goalIDsCiphertext = encryptor.encrypt(goalIDs);
		nicknameCiphertext = encryptor.encrypt(nickname);
		messageCiphertext = encryptor.encrypt(message);
		buddyIDCiphertext = encryptor.encrypt(buddyID);
	}

	@Override
	public void decrypt(Decryptor decryptor) {
		requestingUserID = decryptor.decryptUUID(requestingUserIDCiphertext);
		accessorID = decryptor.decryptUUID(accessorIDCiphertext);
		goalIDs = decryptor.decryptUUIDSet(goalIDsCiphertext);
		nickname = decryptor.decryptString(nicknameCiphertext);
		message = decryptor.decryptString(messageCiphertext);
		buddyID = decryptor.decryptUUID(buddyIDCiphertext);
	}
}
