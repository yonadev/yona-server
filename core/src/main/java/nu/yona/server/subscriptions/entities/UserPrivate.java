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
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageSource;

@Entity
@Table(name = "USERS_PRIVATE")
public class UserPrivate extends EntityWithID {

	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

	@Convert(converter = StringFieldEncrypter.class)
	private String decryptionCheck;

	@Convert(converter = StringFieldEncrypter.class)
	private String nickname;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID accessorID;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID vpnProfileID;

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = StringFieldEncrypter.class)
	private Set<String> deviceNames;

	@Transient
	private Set<Goal> goals = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = UUIDFieldEncrypter.class)
	private Set<UUID> goalIDs;

	@Transient
	private Set<Buddy> buddies = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = UUIDFieldEncrypter.class)
	private Set<UUID> buddyIDs;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID anonymousMessageSourceID;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID namedMessageSourceID;

	// Default constructor is required for JPA
	public UserPrivate() {
		super(null);
	}

	private UserPrivate(UUID id, String nickname, UUID accessorID, UUID vpnProfileID, Set<String> deviceNames,
			Set<Goal> goals, UUID anonymousMessageSourceID, UUID namedMessageSourceID) {
		super(id);
		this.decryptionCheck = buildDecryptionCheck();
		this.nickname = nickname;
		this.accessorID = accessorID;
		this.vpnProfileID = vpnProfileID;
		this.deviceNames = deviceNames;
		setGoals(new HashSet<>(goals));
		this.anonymousMessageSourceID = anonymousMessageSourceID;
		this.namedMessageSourceID = namedMessageSourceID;
	}

	private static String buildDecryptionCheck() {
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
	}

	public static UserPrivate createInstance(String nickname, Accessor accessor, Set<String> deviceNames,
			Set<Goal> goals, MessageSource anonymousMessageSource, MessageSource namedMessageSource) {
		VPNProfile vpnProfile = VPNProfile.createInstance(accessor.getID().toString());
		VPNProfile.getRepository().save(vpnProfile);
		return new UserPrivate(UUID.randomUUID(), nickname, accessor.getID(), vpnProfile.getID(), deviceNames, goals,
				anonymousMessageSource.getID(), namedMessageSource.getID());
	}

	public boolean isDecryptedProperly() {
		return isDecrypted() && decryptionCheck.startsWith(DECRYPTION_CHECK_STRING);
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public UUID getAccessorID() {
		return accessorID;
	}

	public VPNProfile getVPNProfile() {
		return VPNProfile.getRepository().findOne(vpnProfileID);
	}

	public Set<String> getDeviceNames() {
		return Collections.unmodifiableSet(deviceNames);
	}

	public void setDeviceNames(Set<String> deviceNames) {
		this.deviceNames = deviceNames;
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

	public MessageSource getAnonymousMessageSource() {
		return MessageSource.getRepository().findOne(anonymousMessageSourceID);
	}

	public MessageSource getNamedMessageSource() {
		return MessageSource.getRepository().findOne(namedMessageSourceID);
	}

	private boolean isDecrypted() {
		return decryptionCheck != null;
	}
}
