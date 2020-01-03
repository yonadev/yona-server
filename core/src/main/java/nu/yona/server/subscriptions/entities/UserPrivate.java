/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import nu.yona.server.crypto.seckey.DateTimeFieldEncryptor;
import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.messaging.entities.MessageSource;

@Entity
@Table(name = "USERS_PRIVATE")
public class UserPrivate extends PrivateUserProperties
{
	private static final String DECRYPTION_CHECK_STRING = "Decrypted properly#";

	@Convert(converter = StringFieldEncryptor.class)
	private String decryptionCheck;

	@Convert(converter = DateTimeFieldEncryptor.class)
	private LocalDateTime creationTime;

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

	// YD-542 Remove this property
	@Convert(converter = StringFieldEncryptor.class)
	private String vpnPassword;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "user_private_id", referencedColumnName = "id")
	@Fetch(FetchMode.JOIN)
	private Set<UserDevice> devices;

	// Default constructor is required for JPA
	public UserPrivate()
	{
		super();
	}

	private UserPrivate(UUID id, LocalDateTime creationTime, String firstName, String lastName, String nickname,
			UUID userAnonymizedId, UUID anonymousMessageSourceId, UUID namedMessageSourceId)
	{
		super(id, firstName, lastName, nickname, Optional.empty());
		this.decryptionCheck = buildDecryptionCheck();
		this.creationTime = creationTime;
		this.userAnonymizedId = userAnonymizedId;
		this.buddies = new HashSet<>();
		this.anonymousMessageSourceId = anonymousMessageSourceId;
		this.namedMessageSourceId = namedMessageSourceId;
		this.devices = new HashSet<>();
	}

	public static UserPrivate createInstance(LocalDateTime creationTime, String firstName, String lastName, String nickname,
			UUID userAnonymizedId, UUID anonymousMessageSourceId, MessageSource namedMessageSource)
	{
		return new UserPrivate(UUID.randomUUID(), creationTime, firstName, lastName, nickname, userAnonymizedId,
				anonymousMessageSourceId, namedMessageSource.getId());
	}

	private static String buildDecryptionCheck()
	{
		return DECRYPTION_CHECK_STRING + CryptoUtil.getRandomString(DECRYPTION_CHECK_STRING.length());
	}

	public boolean isDecryptedProperly()
	{
		return isDecrypted() && decryptionCheck.startsWith(DECRYPTION_CHECK_STRING);
	}

	UserAnonymized getUserAnonymized()
	{
		return UserAnonymized.getRepository().findById(userAnonymizedId)
				.orElseThrow(() -> InvalidDataException.userAnonymizedIdNotFound(userAnonymizedId));
	}

	public Set<Buddy> getBuddies()
	{
		return Collections.unmodifiableSet(buddies);
	}

	public void addBuddy(Buddy buddy)
	{
		buddies.add(Objects.requireNonNull(buddy));
	}

	public void removeBuddy(Buddy buddy)
	{
		buddies.remove(Objects.requireNonNull(buddy));
	}

	public UUID getAnonymousMessageSourceId()
	{
		return anonymousMessageSourceId;
	}

	public UUID getNamedMessageSourceId()
	{
		return namedMessageSourceId;
	}

	public Optional<String> getAndClearVpnPassword()
	{
		if (vpnPassword == null)
		{
			return Optional.empty();
		}
		Optional<String> retVal = Optional.of(vpnPassword);
		vpnPassword = null;
		return retVal;
	}

	public LocalDateTime getCreationTime()
	{
		return this.creationTime;
	}

	public void setCreationTime(LocalDateTime creationTime)
	{
		this.creationTime = creationTime;
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
		return buddies.stream().filter(b -> !b.getUserIfExists().isPresent()).collect(Collectors.toSet());
	}

	private void loadAllBuddyUsersAtOnce()
	{
		User.getRepository().findAllById(buddies.stream().map(Buddy::getUserId).collect(Collectors.toList()));
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return getUserAnonymized().getLastMonitoredActivityDate();
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		getUserAnonymized().setLastMonitoredActivityDate(lastMonitoredActivityDate);
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
