/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.VpnStatusChangeEvent;

public class DeviceAnonymizedDto implements Serializable
{
	private static final long serialVersionUID = 5735844031070742651L;

	private final UUID id;
	private final int deviceIndex;
	private final OperatingSystem operatingSystem;
	private final LocalDate lastMonitoredActivityDate;
	private final String appVersion;
	private final int appVersionCode;
	private final String vpnLoginId;
	private final boolean isVpnConnected;
	private final String firebaseInstanceId;
	private final Locale locale;

	public DeviceAnonymizedDto(UUID id, int deviceIndex, OperatingSystem operatingSystem,
			Optional<LocalDate> lastMonitoredActivityDate, String appVersion, int appVersionCode, String vpnLoginId,
			boolean isVpnConnected, Optional<String> firebaseInstanceId, Locale locale)
	{
		this.id = id;
		this.deviceIndex = deviceIndex;
		this.operatingSystem = operatingSystem;
		this.lastMonitoredActivityDate = lastMonitoredActivityDate.orElse(null);
		this.appVersion = appVersion;
		this.appVersionCode = appVersionCode;
		this.vpnLoginId = vpnLoginId;
		this.isVpnConnected = isVpnConnected;
		this.firebaseInstanceId = firebaseInstanceId.orElse(null);
		this.locale = locale;
	}

	public static DeviceAnonymizedDto createInstance(DeviceAnonymized entity)
	{
		return new DeviceAnonymizedDto(entity.getId(), entity.getDeviceIndex(), entity.getOperatingSystem(),
				entity.getLastMonitoredActivityDate(), entity.getAppVersion(), entity.getAppVersionCode(), entity.getVpnLoginId(),
				entity.getLastVpnStatusChangeEvent().map(VpnStatusChangeEvent::isVpnConnected).orElse(false),
				entity.getFirebaseInstanceId(), entity.getLocale());
	}

	public UUID getId()
	{
		return id;
	}

	public int getDeviceIndex()
	{
		return deviceIndex;
	}

	public OperatingSystem getOperatingSystem()
	{
		return operatingSystem;
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return Optional.ofNullable(lastMonitoredActivityDate);
	}

	public String getAppVersion()
	{
		return appVersion;
	}

	public int getAppVersionCode()
	{
		return appVersionCode;
	}

	public String getVpnLoginId()
	{
		return vpnLoginId;
	}

	public boolean isVpnConnected()
	{
		return isVpnConnected;
	}

	public Optional<String> getFirebaseInstanceId()
	{
		return Optional.ofNullable(firebaseInstanceId);
	}

	public Locale getLocale()
	{
		return locale;
	}
}