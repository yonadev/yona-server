/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "DEVICES_ANONYMIZED")
public class DeviceAnonymized extends EntityWithUuid
{
	public enum OperatingSystem
	{
		UNKNOWN, ANDROID, IOS
	}

	@ManyToOne
	private UserAnonymized userAnonymized;

	private int deviceIndex;

	private OperatingSystem operatingSystem;

	private LocalDate lastMonitoredActivityDate;

	private String appVersion;

	// Default constructor is required for JPA
	protected DeviceAnonymized()
	{
		super(null);
	}

	private DeviceAnonymized(UUID id, int deviceIndex, OperatingSystem operatingSystem, String appVersion)
	{
		super(id);
		this.deviceIndex = deviceIndex;
		this.operatingSystem = operatingSystem;
		this.appVersion = Objects.requireNonNull(appVersion);
	}

	public static DeviceAnonymized createInstance(int deviceIndex, OperatingSystem operatingSystem, String appVersion)
	{
		return new DeviceAnonymized(UUID.randomUUID(), deviceIndex, operatingSystem, appVersion);
	}

	public static DeviceAnonymizedRepository getRepository()
	{
		return (DeviceAnonymizedRepository) RepositoryProvider.getRepository(DeviceAnonymized.class, UUID.class);
	}

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
	}

	public void setUserAnonymized(UserAnonymized userAnonymized)
	{
		this.userAnonymized = Objects.requireNonNull(userAnonymized);
	}

	public void clearUserAnonymized()
	{
		this.userAnonymized = null;
	}

	public int getDeviceIndex()
	{
		return deviceIndex;
	}

	public OperatingSystem getOperatingSystem()
	{
		return operatingSystem;
	}

	public void setOperatingSystem(OperatingSystem operatingSystem)
	{
		this.operatingSystem = operatingSystem;
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return Optional.ofNullable(lastMonitoredActivityDate);
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		this.lastMonitoredActivityDate = Objects.requireNonNull(lastMonitoredActivityDate);
	}

	public String getAppVersion()
	{
		return appVersion;
	}

	public void setAppVersion(String appVersion)
	{
		this.appVersion = Objects.requireNonNull(appVersion);
	}

	public String getVpnLoginId()
	{
		return userAnonymized.getId() + "-" + getDeviceIndex();
	}
}