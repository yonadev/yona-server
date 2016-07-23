/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import nu.yona.server.crypto.ByteFieldEncrypter;
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

	@Column(nullable = true)
	private int touchVersion;

	@Convert(converter = StringFieldEncrypter.class)
	private String decryptionCheck;

	@Convert(converter = StringFieldEncrypter.class)
	private String nickname;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID userAnonymizedID;

	@OneToMany(cascade = CascadeType.ALL)
	private Set<Buddy> buddies;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID anonymousMessageSourceID;

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID namedMessageSourceID;

	@Convert(converter = StringFieldEncrypter.class)
	private String vpnPassword;

	@Convert(converter = ByteFieldEncrypter.class)
	@Column(length = 1024)
	private byte[] vpnAuthCertificateByteArray;

	// Default constructor is required for JPA
	public UserPrivate()
	{
		super(null);
	}

	private UserPrivate(UUID id, String nickname, UUID userAnonymizedID, String vpnPassword, UUID anonymousMessageSourceID,
			UUID namedMessageSourceID)
	{
		super(id);
		this.decryptionCheck = buildDecryptionCheck();
		this.nickname = nickname;
		this.userAnonymizedID = userAnonymizedID;
		this.vpnPassword = vpnPassword;
		this.buddies = new HashSet<>();
		this.anonymousMessageSourceID = anonymousMessageSourceID;
		this.namedMessageSourceID = namedMessageSourceID;
		this.vpnAuthCertificateByteArray = generateVPNAuthCertificateByteArray();
	}

	private byte[] generateVPNAuthCertificateByteArray()
	{
		// TODO: generate, make sure Smoothwall accepts it
		return new byte[0];
	}

	private static String buildDecryptionCheck()
	{
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
	}

	public static UserPrivate createInstance(String nickname, String vpnPassword, Set<Goal> goals,
			MessageSource anonymousMessageSource, MessageSource namedMessageSource)
	{
		UserAnonymized userAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(), goals);
		UserAnonymized.getRepository().save(userAnonymized);
		return new UserPrivate(UUID.randomUUID(), nickname, userAnonymized.getID(), vpnPassword, anonymousMessageSource.getID(),
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

	UserAnonymized getUserAnonymized()
	{
		return UserAnonymized.getRepository().findOne(userAnonymizedID);
	}

	public Set<Buddy> getBuddies()
	{
		return Collections.unmodifiableSet(buddies);
	}

	public void addBuddy(Buddy buddy)
	{
		buddies.add(buddy);
	}

	public void removeBuddy(Buddy buddy)
	{
		buddies.remove(buddy);
	}

	public void removeBuddyForUserID(UUID userID)
	{
		buddies.removeIf(buddy -> buddy.getUserID().equals(userID));
	}

	public MessageSource getAnonymousMessageSource()
	{
		return MessageSource.getRepository().findOne(anonymousMessageSourceID);
	}

	public MessageSource getNamedMessageSource()
	{
		return MessageSource.getRepository().findOne(namedMessageSourceID);
	}

	public UUID getVPNLoginID()
	{
		// these are the same for performance
		return userAnonymizedID;
	}

	public String getVPNPassword()
	{
		return vpnPassword;
	}

	public byte[] getVPNAuthCertificateByteArray()
	{
		return vpnAuthCertificateByteArray;
	}

	private boolean isDecrypted()
	{
		return decryptionCheck != null;
	}

	public void touch()
	{
		touchVersion++;
	}

	public UUID getUserAnonymizedID()
	{
		return userAnonymizedID;
	}

	public Set<Buddy> getBuddiesRelatedToRemovedUsers()
	{
		return buddies.stream().filter(b -> b.getUser() == null).collect(Collectors.toSet());
	}
}
