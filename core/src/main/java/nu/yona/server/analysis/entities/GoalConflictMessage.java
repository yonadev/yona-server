/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.Decryptor;
import nu.yona.server.crypto.Encryptor;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.Message;

@Entity
public class GoalConflictMessage extends Message {

	@Transient
	private UUID accessorID;
	private byte[] accessorIDCiphertext;

	@Transient
	private UUID goalID;
	private byte[] goalIDCiphertext;

	@Transient
	private String url;
	private byte[] urlCiphertext;

	// Default constructor is required for JPA
	public GoalConflictMessage() {
		super(null);
	}

	public GoalConflictMessage(UUID id, UUID accessorID, UUID goalID, String url) {
		super(id);

		this.accessorID = accessorID;
		this.goalID = goalID;
		this.url = url;
	}

	public Goal getGoal() {
		return Goal.getRepository().findOne(goalID);
	}

	public String getURL() {
		return url;
	}

	public UUID getAccessorID() {
		return accessorID;
	}

	@Override
	public void encrypt(Encryptor encryptor) {
		accessorIDCiphertext = encryptor.encrypt(accessorID);
		goalIDCiphertext = encryptor.encrypt(goalID);
		urlCiphertext = encryptor.encrypt(url);
	}

	@Override
	public void decrypt(Decryptor decryptor) {
		accessorID = decryptor.decryptUUID(accessorIDCiphertext);
		goalID = decryptor.decryptUUID(goalIDCiphertext);
		url = decryptor.decryptString(urlCiphertext);
	}

	public static GoalConflictMessage createInstance(UUID accessorID, Goal goal, String url) {
		return new GoalConflictMessage(UUID.randomUUID(), accessorID, goal.getID(), url);
	}
}
