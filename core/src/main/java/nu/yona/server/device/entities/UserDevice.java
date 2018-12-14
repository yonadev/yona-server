/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.crypto.seckey.DateFieldEncryptor;
import nu.yona.server.crypto.seckey.DateTimeFieldEncryptor;
import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "USER_DEVICES")
public class UserDevice extends DeviceBase
{
	@Type(type = "uuid-char")
	@Column(name = "user_private_id")
	private UUID userPrivateId;

	private boolean isLegacyVpnAccount;

	@Convert(converter = StringFieldEncryptor.class)
	private String vpnPassword;

	@Convert(converter = DateTimeFieldEncryptor.class)
	private LocalDateTime registrationTime;

	@Convert(converter = DateFieldEncryptor.class)
	private LocalDate appLastOpenedDate;

	// Default constructor is required for JPA
	public UserDevice()
	{
	}

	private UserDevice(UUID id, String name, UUID deviceAnonymizedId, String vpnPassword, LocalDateTime registrationTime)
	{
		super(id, name, deviceAnonymizedId);
		this.registrationTime = registrationTime;
		this.vpnPassword = Objects.requireNonNull(vpnPassword);
	}

	public static UserDevice createInstance(User userEntity, String name, UUID deviceAnonymizedId, String vpnPassword)
	{
		UserDevice newDevice = new UserDevice(UUID.randomUUID(), name, deviceAnonymizedId, vpnPassword, TimeUtil.utcNow());
		// The user registers this device, so the app is open now
		newDevice.setAppLastOpenedDateToNow(userEntity);
		return newDevice;
	}

	public UUID getUserPrivateId()
	{
		return userPrivateId;
	}

	public void setUserPrivateId(UUID userPrivateId)
	{
		this.userPrivateId = userPrivateId;
	}

	public void clearUserPrivateId()
	{
		userPrivateId = null;
	}

	public boolean isLegacyVpnAccount()
	{
		return isLegacyVpnAccount;
	}

	public String getVpnPassword()
	{
		return vpnPassword;
	}

	public void setLegacyVpnAccountPassword(String vpnPassword)
	{
		this.vpnPassword = vpnPassword;
		this.isLegacyVpnAccount = true;
	}

	public LocalDateTime getRegistrationTime()
	{
		return registrationTime;
	}

	public void setRegistrationTime(LocalDateTime registrationTime)
	{
		this.registrationTime = Objects.requireNonNull(registrationTime);
	}

	public LocalDate getAppLastOpenedDate()
	{
		return appLastOpenedDate;
	}

	public void setAppLastOpenedDateToNow(User userEntity)
	{
		LocalDate today = userEntity.getDateInUserTimezone(TimeUtil.utcNow());

		this.appLastOpenedDate = Objects.requireNonNull(today);
		userEntity.setAppLastOpenedDate(today);
	}
}
