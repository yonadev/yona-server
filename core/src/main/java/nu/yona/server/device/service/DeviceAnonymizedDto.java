/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.io.Serializable;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;

public class DeviceAnonymizedDto implements Serializable
{
	private static final long serialVersionUID = 5735844031070742651L;

	private final UUID id;
	private final int deviceIndex;
	private final OperatingSystem operatingSystem;
	private final String vpnLoginId;
	private final String firebaseInstanceId;
	private final Locale locale;

	public DeviceAnonymizedDto(UUID id, int deviceIndex, OperatingSystem operatingSystem, String vpnLoginId,
			Optional<String> firebaseInstanceId, Locale locale)
	{
		this.id = id;
		this.deviceIndex = deviceIndex;
		this.operatingSystem = operatingSystem;
		this.vpnLoginId = vpnLoginId;
		this.firebaseInstanceId = firebaseInstanceId.orElse(null);
		this.locale = locale;
	}

	public static DeviceAnonymizedDto createInstance(DeviceAnonymized entity)
	{
		return new DeviceAnonymizedDto(entity.getId(), entity.getDeviceIndex(), entity.getOperatingSystem(),
				entity.getVpnLoginId(), entity.getFirebaseInstanceId(), entity.getLocale());
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

	public String getVpnLoginId()
	{
		return vpnLoginId;
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