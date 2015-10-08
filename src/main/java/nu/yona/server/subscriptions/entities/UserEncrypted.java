/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Table;
import javax.persistence.Transient;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.crypto.UUIDFieldEncrypter;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.model.EntityWithID;

@Entity
@Table(name = "USERS_ENCRYPTED")
public class UserEncrypted extends EntityWithID {

	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

	@Convert(converter = StringFieldEncrypter.class)
	private String decryptionCheck;

	@Convert(converter = StringFieldEncrypter.class)
	private String nickname;

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = StringFieldEncrypter.class)
	private Set<String> deviceNames;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID accessorID;

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = UUIDFieldEncrypter.class)
	private Set<UUID> goalIDs;

	@Transient
	private Set<Goal> goals = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = UUIDFieldEncrypter.class)
	private Set<UUID> buddyIDs;

	@Transient
	private Set<Buddy> buddies = new HashSet<>();

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID anonymousMessageSourceID;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID namedMessageSourceID;

	// Default constructor is required for JPA
	public UserEncrypted() {
		super(null);
	}

	private UserEncrypted(UUID id, String nickname, Set<String> deviceNames, Set<Goal> goals, UUID accessorID,
			UUID anonymousMessageSourceID, UUID namedMessageSourceID) {
		super(id);
		this.decryptionCheck = buildDecryptionCheck();
		this.nickname = nickname;
		this.deviceNames = deviceNames;
		setGoals(new HashSet<>(goals));
		this.accessorID = accessorID;
		this.anonymousMessageSourceID = anonymousMessageSourceID;
		this.namedMessageSourceID = namedMessageSourceID;
	}

	private static String buildDecryptionCheck() {
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
	}

	public static UserEncrypted createInstance(String nickname, Set<String> deviceNames, Set<Goal> goals,
			Accessor accessor, MessageSource anonymousMessageSource, MessageSource namedMessageSource) {
		return new UserEncrypted(UUID.randomUUID(), nickname, deviceNames, goals, accessor.getID(),
				anonymousMessageSource.getID(), namedMessageSource.getID());
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public Set<String> getDeviceNames() {
		return Collections.unmodifiableSet(deviceNames);
	}

	public void setDeviceNames(Set<String> deviceNames) {
		this.deviceNames = deviceNames;
	}

	public UUID getAccessorID() {
		return accessorID;
	}

	public Set<Goal> getGoals() {
		if (goals.size() == 0) {
			goals = goalIDs.stream().map(id -> Goal.getRepository().findOne(id)).collect(Collectors.toSet());
		}
		return Collections.unmodifiableSet(goals);
	}

	public void setGoals(Set<Goal> goals) {
		this.goals = new HashSet<>(goals);
		goalIDs = goals.stream().map(g -> g.getID()).collect(Collectors.toSet());
	}

	public Set<Buddy> getBuddies() {
		if (buddies.size() == 0) {
			buddies = buddyIDs.stream().map(id -> Buddy.getRepository().findOne(id)).collect(Collectors.toSet());
		}
		return Collections.unmodifiableSet(buddies);
	}

	public void addBuddy(Buddy buddy) {
		buddies.add(buddy);
		buddyIDs.add(buddy.getID());
	}

	public MessageSource getNamedMessageSource() {
		return MessageSource.getRepository().findOne(namedMessageSourceID);
	}

	public MessageSource getAnonymousMessageSource() {
		return MessageSource.getRepository().findOne(anonymousMessageSourceID);
	}

	public boolean isDecryptedProperly() {
		return isDecrypted() && decryptionCheck.startsWith(DECRYPTION_CHECK_STRING);
	}

	private boolean isDecrypted() {
		return decryptionCheck != null;
	}
}
