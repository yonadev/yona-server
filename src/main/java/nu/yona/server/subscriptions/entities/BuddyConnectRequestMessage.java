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

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.subscriptions.entities.Buddy.Status;

@Entity
public class BuddyConnectRequestMessage extends Message {

	@ManyToOne
	private User requestingUser;

	@Transient
	private UUID accessorID;
	private byte[] accessorIDCiphertext;

	@ElementCollection
	private Set<Goal> goals;

	@Transient
	private String nickname;
	private byte[] nicknameCiphertext;

	@Transient
	private String message;
	private byte[] messageCiphertext;

	@Transient
	private long buddyID;
	private byte[] buddyIDCiphertext;

	private Status status = Status.NOT_REQUESTED;

	public BuddyConnectRequestMessage() {
		super(null);
		// Default constructor is required for JPA
	}

	private BuddyConnectRequestMessage(UUID id, User requestingUser, UUID accessorID, Set<Goal> goals, String nickname,
			String message, long buddyID) {
		super(id);
		this.buddyID = buddyID;
		if (requestingUser == null) {
			throw new IllegalArgumentException("requestingUser cannot be null");
		}
		if (accessorID == null) {
			throw new IllegalArgumentException("accessorID cannot be null");
		}
		this.requestingUser = requestingUser;
		this.accessorID = accessorID;
		this.goals = goals;
		this.nickname = nickname;
		this.message = message;
	}

	public User getRequestingUser() {
		return requestingUser;
	}

	public UUID getAccessorID() {
		if (accessorID == null) {
			throw new IllegalStateException("Message has not been decrypted");
		}
		return accessorID;
	}

	public Set<Goal> getGoals() {
		return goals;
	}

	public String getNickname() {
		return nickname;
	}

	public String getMessage() {
		return message;
	}

	public long getBuddyID() {
		return buddyID;
	}

	public boolean isAccepted() {
		return status == Status.ACCEPTED;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public static BuddyConnectRequestMessage createInstance(User requestingUser, String nickname, String message,
			Set<Goal> goals, long buddyID) {
		return new BuddyConnectRequestMessage(UUID.randomUUID(), requestingUser, requestingUser.getAccessorID(), goals,
				nickname, message, buddyID);
	}

	@Override
	public void encrypt(Encryptor encryptor) {
		messageCiphertext = encryptor.encrypt(message);
		accessorIDCiphertext = encryptor.encrypt(accessorID);
		nicknameCiphertext = encryptor.encrypt(nickname);
		buddyIDCiphertext = encryptor.encrypt(buddyID);
	}

	@Override
	public void decrypt(Decryptor decryptor) {
		message = decryptor.decryptString(messageCiphertext);
		accessorID = decryptor.decryptUUID(accessorIDCiphertext);
		nickname = decryptor.decryptString(nicknameCiphertext);
		buddyID = decryptor.decryptLong(buddyIDCiphertext);
	}
}
