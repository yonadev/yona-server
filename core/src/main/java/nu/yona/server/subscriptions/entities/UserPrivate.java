/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.messaging.entities.MessageSource;

@Entity
@Table(name = "USERS_PRIVATE")
public class UserPrivate extends EntityWithUuid
{

	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

	@Column(nullable = true)
	private int touchVersion;

	@Convert(converter = StringFieldEncryptor.class)
	private String decryptionCheck;

	@Convert(converter = StringFieldEncryptor.class)
	private String nickname;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID userAnonymizedId;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "owning_user_private_id", referencedColumnName = "id")
	@Fetch(FetchMode.JOIN)
	private Set<Buddy> buddies;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID anonymousMessageSourceId;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID namedMessageSourceId;

	@Convert(converter = StringFieldEncryptor.class)
	private String vpnPassword;

	// Default constructor is required for JPA
	public UserPrivate()
	{
		super(null);
	}

	private UserPrivate(UUID id, String nickname, UUID userAnonymizedId, String vpnPassword, UUID anonymousMessageSourceId,
			UUID namedMessageSourceId)
	{
		super(id);
		this.decryptionCheck = buildDecryptionCheck();
		this.nickname = nickname;
		this.userAnonymizedId = userAnonymizedId;
		this.vpnPassword = vpnPassword;
		this.buddies = new HashSet<>();
		this.anonymousMessageSourceId = anonymousMessageSourceId;
		this.namedMessageSourceId = namedMessageSourceId;
	}

	private static String buildDecryptionCheck()
	{
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
	}

	public static UserPrivate createInstance(String nickname, String vpnPassword, UUID userAnonymizedId,
			UUID anonymousMessageSourceId, MessageSource namedMessageSource)
	{
		return new UserPrivate(UUID.randomUUID(), nickname, userAnonymizedId, vpnPassword, anonymousMessageSourceId,
				namedMessageSource.getId());
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
		UserAnonymized userAnonymized = UserAnonymized.getRepository().findOne(userAnonymizedId);
		Objects.requireNonNull(userAnonymized, "UserAnonymized with ID " + userAnonymizedId + " not found");
		return userAnonymized;
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

	public UUID getAnonymousMessageSourceId()
	{
		return anonymousMessageSourceId;
	}

	public UUID getNamedMessageSourceId()
	{
		return namedMessageSourceId;
	}

	public UUID getVpnLoginId()
	{
		// these are the same for performance
		return userAnonymizedId;
	}

	public String getVpnPassword()
	{
		return vpnPassword;
	}

	private boolean isDecrypted()
	{
		return decryptionCheck != null;
	}

	public void touch()
	{
		touchVersion++;
	}

	public UUID getUserAnonymizedId()
	{
		return userAnonymizedId;
	}

	public Set<Buddy> getBuddiesRelatedToRemovedUsers()
	{
		loadAllBuddyUsers();
		return buddies.stream().filter(b -> b.getUser() == null).collect(Collectors.toSet());
	}

	private void loadAllBuddyUsers()
	{
		User.getRepository().findAll(buddies.stream().map(Buddy::getUserId).collect(Collectors.toList()));
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return getUserAnonymized().getLastMonitoredActivityDate();
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		getUserAnonymized().setLastMonitoredActivityDate(lastMonitoredActivityDate);
	}
}
