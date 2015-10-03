/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.PublicKeyEncryptor;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.model.RepositoryProvider;

@Entity
@Table(name = "MESSAGE_DESTINATIONS")
public class MessageDestination {
	public static MessageDestinationRepository getRepository() {
		return (MessageDestinationRepository) RepositoryProvider.getRepository(MessageDestination.class, Long.class);
	}

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(length = 1024)
	private byte[] publicKeyBytes;

	@OneToMany(cascade = CascadeType.ALL)
	private List<Message> messages;

	@Transient
	private PublicKey publicKey;

	public MessageDestination() {
		// Default constructor is required for JPA
	}

	public MessageDestination(PublicKey publicKey) {
		this.publicKeyBytes = PublicKeyUtil.publicKeyToBytes(publicKey);
		this.messages = new ArrayList<>();
	}

	public long getID() {
		return id;
	}

	public void send(Message message) {
		message.encrypt(PublicKeyEncryptor.createInstance(loadPublicKey()));
		messages.add(message);
	}

	public List<Message> getAllMessages() {
		return Collections.unmodifiableList(messages);
	}

	private PublicKey loadPublicKey() {
		if (publicKey == null) {
			publicKey = PublicKeyUtil.publicKeyFromBytes(publicKeyBytes);
		}
		return publicKey;
	}
}
