/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "DEVICES_ANONYMIZED")
public abstract class DeviceAnonymized extends EntityWithUuid
{
	enum OperatingSystem
	{
		UNKNOWN, ANDROID, IOS
	}

	@ManyToOne
	private UserAnonymized userAnonymized;

	private int deviceId;

	private OperatingSystem operatingSystem;

	private LocalDate lastMonitoredActivityDate;

	// Default constructor is required for JPA
	protected DeviceAnonymized()
	{
		super(null);
	}

	protected DeviceAnonymized(UUID id, UserAnonymized userAnonymized, int deviceId, OperatingSystem operatingSystem)
	{
		super(id);
		this.userAnonymized = Objects.requireNonNull(userAnonymized);
		this.deviceId = deviceId;
		this.operatingSystem = operatingSystem;
	}

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
	}

	public void setUserAnonymized(UserAnonymized userAnonymized)
	{
		this.userAnonymized = userAnonymized;
	}

	public void clearUserAnonymized()
	{
		this.userAnonymized = null;
	}

	public int getDeviceId()
	{
		return deviceId;
	}

	public OperatingSystem getOperatingSystem()
	{
		return operatingSystem;
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return Optional.ofNullable(lastMonitoredActivityDate);
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		this.lastMonitoredActivityDate = Objects.requireNonNull(lastMonitoredActivityDate);
	}

	public String getVpnLoginId()
	{
		return userAnonymized.getId() + "-" + getDeviceId();
	}
}