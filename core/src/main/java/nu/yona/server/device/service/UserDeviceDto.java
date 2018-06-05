/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.subscriptions.service.VPNProfileDto;
import nu.yona.server.util.TimeUtil;

@JsonRootName("device")
public class UserDeviceDto extends DeviceBaseDto
{
	private final LocalDateTime registrationTime;
	private final OperatingSystem operatingSystem;
	private final String appVersion;
	private final Optional<String> firebaseInstanceId;
	private final LocalDate appLastOpenedDate;
	private final UUID deviceAnonymizedId;
	private final VPNProfileDto vpnProfile;

	public UserDeviceDto(String name, OperatingSystem operatingSystem, String appVersion, Optional<String> firebaseInstanceId)
	{
		this(null, name, operatingSystem, appVersion, firebaseInstanceId, true, LocalDateTime.now(), LocalDate.now(), null, null);
	}

	private UserDeviceDto(UUID id, String name, OperatingSystem operatingSystem, String appVersion,
			Optional<String> firebaseInstanceId, boolean isVpnConnected, LocalDateTime registrationTime,
			LocalDate appLastOpenedDate, UUID deviceAnonymizedId, VPNProfileDto vpnProfile)
	{
		super(id, name, isVpnConnected);
		this.operatingSystem = operatingSystem;
		this.appVersion = appVersion;
		this.firebaseInstanceId = firebaseInstanceId;
		this.registrationTime = registrationTime;
		this.appLastOpenedDate = appLastOpenedDate;
		this.deviceAnonymizedId = deviceAnonymizedId;
		this.vpnProfile = vpnProfile;
	}

	public OperatingSystem getOperatingSystem()
	{
		return operatingSystem;
	}

	@JsonIgnore
	public String getAppVersion()
	{
		return appVersion;
	}

	@JsonIgnore
	public LocalDateTime getRegistrationTime()
	{
		return registrationTime;
	}

	@JsonProperty("registrationTime")
	@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
	public ZonedDateTime getRegistrationTimeAsZonedDateTime()
	{
		return TimeUtil.toUtcZonedDateTime(registrationTime);
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public LocalDate getAppLastOpenedDate()
	{
		return appLastOpenedDate;
	}

	@JsonIgnore
	public UUID getDeviceAnonymizedId()
	{
		return deviceAnonymizedId;
	}

	public static UserDeviceDto createInstance(UserDevice deviceEntity)
	{
		DeviceAnonymized deviceAnonymized = deviceEntity.getDeviceAnonymized();
		return new UserDeviceDto(deviceEntity.getId(), deviceEntity.getName(), deviceAnonymized.getOperatingSystem(),
				deviceAnonymized.getAppVersion(), deviceAnonymized.getFirebaseInstanceId(), deviceEntity.isVpnConnected(),
				deviceEntity.getRegistrationTime(), deviceEntity.getAppLastOpenedDate(), deviceEntity.getDeviceAnonymizedId(),
				VPNProfileDto.createInstance(deviceEntity));
	}

	public static UserDeviceDto createDeviceRegistrationInstance(DeviceRegistrationRequestDto deviceRegistration)
	{
		return new UserDeviceDto(deviceRegistration.name,
				parseOperatingSystemOfRegistrationRequest(deviceRegistration.operatingSystemStr), deviceRegistration.appVersion,
				deviceRegistration.firebaseInstanceId);
	}

	public static OperatingSystem parseOperatingSystemOfRegistrationRequest(String operatingSystemStr)
	{
		try
		{
			OperatingSystem operatingSystem = OperatingSystem.valueOf(operatingSystemStr);
			if (operatingSystem != OperatingSystem.UNKNOWN)
			{
				return operatingSystem;
			}
		}
		catch (IllegalArgumentException e)
		{
			// Handle below
		}
		throw InvalidDataException.invalidOperatingSystem(operatingSystemStr);
	}

	public VPNProfileDto getVpnProfile()
	{
		return vpnProfile;
	}

	public Optional<String> getFirebaseInstanceId()
	{
		return firebaseInstanceId;
	}
}
