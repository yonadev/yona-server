/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "USER_DEVICES")
public class UserDevice extends DeviceBase
{
	@Type(type = "uuid-char")
	@Column(name = "user_private_id")
	private UUID userPrivateId;

	@Convert(converter = DateTimeFieldEncryptor.class)
	private LocalDateTime registrationTime;

	@Convert(converter = DateFieldEncryptor.class)
	private LocalDate appLastOpenedDate;

	// Default constructor is required for JPA
	public UserDevice()
	{
	}

	private UserDevice(UUID id, String name, UUID deviceAnonymizedId)
	{
		super(id, name, deviceAnonymizedId, true); // Consider the VPN to be connected by default
		this.registrationTime = TimeUtil.utcNow();
		this.appLastOpenedDate = TimeUtil.utcNow().toLocalDate(); // The user registers this device, so the app is open now
	}

	public static UserDevice createInstance(String name, UUID deviceAnonymizedId)
	{
		return new UserDevice(UUID.randomUUID(), name, deviceAnonymizedId);
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

	public void setAppLastOpenedDate(LocalDate appLastOpenedDate)
	{
		this.appLastOpenedDate = Objects.requireNonNull(appLastOpenedDate);
	}
}
