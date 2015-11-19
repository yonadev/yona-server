/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.crypto.UUIDFieldEncrypter;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageSource;

@Entity
@Table(name = "USERS_PRIVATE")
public class UserPrivate extends EntityWithID
{

	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

	@Convert(converter = StringFieldEncrypter.class)
	private String decryptionCheck;

	@Convert(converter = StringFieldEncrypter.class)
	private String nickname;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID userAnonymizedID;

	@ElementCollection(fetch = FetchType.EAGER)
	@Convert(converter = StringFieldEncrypter.class)
	private Set<String> deviceNames;

	@OneToMany(cascade = CascadeType.ALL)
	private Set<Buddy> buddies;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID anonymousMessageSourceID;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID namedMessageSourceID;

	// Default constructor is required for JPA
	public UserPrivate()
	{
		super(null);
	}

	private UserPrivate(UUID id, String nickname, UUID userAnonymizedID, Set<String> deviceNames, UUID anonymousMessageSourceID,
			UUID namedMessageSourceID)
	{
		super(id);
		this.decryptionCheck = buildDecryptionCheck();
		this.nickname = nickname;
		this.userAnonymizedID = userAnonymizedID;
		this.deviceNames = deviceNames;
		this.buddies = new HashSet<>();
		this.anonymousMessageSourceID = anonymousMessageSourceID;
		this.namedMessageSourceID = namedMessageSourceID;
	}

	private static String buildDecryptionCheck()
	{
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
	}

	public static UserPrivate createInstance(String nickname, Set<String> deviceNames, Set<Goal> goals,
			MessageSource anonymousMessageSource, MessageSource namedMessageSource)
	{
		UserAnonymized userAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(), goals);
		UserAnonymized.getRepository().save(userAnonymized);
		return new UserPrivate(UUID.randomUUID(), nickname, userAnonymized.getID(), deviceNames, anonymousMessageSource.getID(),
				namedMessageSource.getID());
	}

	public boolean isDecryptedProperly()
	{
		return isDecrypted() && decryptionCheck.startsWith(DECRYPTION_CHECK_STRING);
	}

	public String getNickname()
	{
		return nickname;
	}

	public void setNickname(String nickname)
	{
		this.nickname = nickname;
	}

	public UserAnonymized getUserAnonymized()
	{
		return UserAnonymized.getRepository().findOne(userAnonymizedID);
	}

	public Set<String> getDeviceNames()
	{
		return Collections.unmodifiableSet(deviceNames);
	}

	public void setDeviceNames(Set<String> deviceNames)
	{
		this.deviceNames = deviceNames;
	}

	public Set<Buddy> getBuddies()
	{
		return Collections.unmodifiableSet(buddies);
	}

	public void addBuddy(Buddy buddy)
	{
		buddies.add(buddy);
		UserAnonymized userAnonymized = getUserAnonymized();
		userAnonymized.addBuddyAnonymized(buddy.getBuddyAnonymized());
		UserAnonymized.getRepository().save(userAnonymized);
	}

	public MessageSource getAnonymousMessageSource()
	{
		return MessageSource.getRepository().findOne(anonymousMessageSourceID);
	}

	public MessageSource getNamedMessageSource()
	{
		return MessageSource.getRepository().findOne(namedMessageSourceID);
	}

	public UUID getLoginID()
	{
		return getUserAnonymized().getLoginID();
	}

	private boolean isDecrypted()
	{
		return decryptionCheck != null;
	}
}
