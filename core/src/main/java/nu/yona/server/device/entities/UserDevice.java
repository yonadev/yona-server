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
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
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

	private OperatingSystem operatingSystem;

	private String appVersion;

	// Default constructor is required for JPA
	public UserDevice()
	{
	}

	private UserDevice(UUID id, String name, OperatingSystem operatingSystem, String appVersion, String vpnPassword)
	{
		super(id, name, null);
		this.operatingSystem = operatingSystem;
		this.appVersion = appVersion;
		this.vpnPassword = Objects.requireNonNull(vpnPassword);
		this.registrationTime = TimeUtil.utcNow();
		this.appLastOpenedDate = TimeUtil.utcNow().toLocalDate(); // The user registers this device, so the app is open now
	}

	public static UserDevice createInstance(String name, OperatingSystem operatingSystem, String appVersion, String vpnPassword)
	{
		return new UserDevice(UUID.randomUUID(), name, operatingSystem, appVersion, vpnPassword);
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

	public OperatingSystem getOperatingSystem()
	{
		return (operatingSystem == null) ? getDeviceAnonymized().getOperatingSystem() : operatingSystem;
	}

	public String getAppVersion()
	{
		return (appVersion == null) ? getDeviceAnonymized().getAppVersion() : appVersion;
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

	public void setAppLastOpenedDate(LocalDate appLastOpenedDate)
	{
		this.appLastOpenedDate = Objects.requireNonNull(appLastOpenedDate);
	}

	public void attachDeviceAnonymized(DeviceAnonymized deviceAnonymized)
	{
		setDeviceAnonymizeId(deviceAnonymized.getId());
		operatingSystem = null;
		appVersion = null;
	}
}
