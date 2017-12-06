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
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.messaging.entities.MessageSource;

@Entity
@Table(name = "USERS_PRIVATE")
public class UserPrivate extends EntityWithUuidAndTouchVersion
{

	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

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

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID userPhotoId;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "user_private_id", referencedColumnName = "id")
	@Fetch(FetchMode.JOIN)
	private Set<UserDevice> devices;

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
		this.devices = new HashSet<>();
	}

	public static UserPrivate createInstance(String nickname, String vpnPassword, UUID userAnonymizedId,
			UUID anonymousMessageSourceId, MessageSource namedMessageSource)
	{
		return new UserPrivate(UUID.randomUUID(), nickname, userAnonymizedId, vpnPassword, anonymousMessageSourceId,
				namedMessageSource.getId());
	}

	private static String buildDecryptionCheck()
	{
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
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
		return Objects.requireNonNull(userAnonymized, "UserAnonymized with ID " + userAnonymizedId + " not found");
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

	@Override
	public UserPrivate touch()
	{
		super.touch();
		return this;
	}

	public UUID getUserAnonymizedId()
	{
		return userAnonymizedId;
	}

	public Set<Buddy> getBuddiesRelatedToRemovedUsers()
	{
		loadAllBuddyUsersAtOnce();
		return buddies.stream().filter(b -> b.getUser() == null).collect(Collectors.toSet());
	}

	private void loadAllBuddyUsersAtOnce()
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

	public Optional<UUID> getUserPhotoId()
	{
		return Optional.ofNullable(userPhotoId);
	}

	public void setUserPhotoId(Optional<UUID> userPhotoId)
	{
		this.userPhotoId = userPhotoId.orElse(null);
	}

	public Set<UserDevice> getDevices()
	{
		return Collections.unmodifiableSet(devices);
	}

	public void addDevice(UserDevice device)
	{
		devices.add(device);
		device.setUserPrivateId(getId());
		getUserAnonymized().addDeviceAnonymized(device.getDeviceAnonymized());
	}

	public void removeDevice(UserDevice device)
	{
		devices.remove(device);
		device.clearUserPrivateId();
	}
}
