/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.model;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
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

	@ManyToOne
	private Goal goal;

	@Transient
	private String url;
	private byte[] urlCiphertext;

	public GoalConflictMessage() {
		// Default constructor is required for JPA
	}

	public GoalConflictMessage(UUID id, UUID accessorID, Goal goal, String url) {
		super(id);

		this.accessorID = accessorID;
		this.goal = goal;
		this.url = url;
	}

	public Goal getGoal() {
		return goal;
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
		urlCiphertext = encryptor.encrypt(url);
	}

	@Override
	public void decrypt(Decryptor decryptor) {
		accessorID = decryptor.decryptUUID(accessorIDCiphertext);
		url = decryptor.decryptString(urlCiphertext);
	}

	public static GoalConflictMessage createInstance(UUID accessorID, Goal goal, String url) {
		return new GoalConflictMessage(UUID.randomUUID(), accessorID, goal, url);
	}
}
