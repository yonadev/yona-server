/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.util.TimeUtil;

@JsonRootName("device")
public class UserDeviceDto extends DeviceBaseDto
{
	private final LocalDateTime registrationTime;
	private final OperatingSystem operatingSystem;
	private final String appVersion;
	private final LocalDate appLastOpenedDate;
	private final UUID deviceAnonymizedId;

	public UserDeviceDto(String name, OperatingSystem operatingSystem, String appVersion)
	{
		this(null, name, operatingSystem, appVersion, true, LocalDateTime.now(), LocalDate.now(), null);
	}

	private UserDeviceDto(UUID id, String name, OperatingSystem operatingSystem, String appVersion, boolean isVpnConnected,
			LocalDateTime registrationTime, LocalDate appLastOpenedDate, UUID deviceAnonymizedId)
	{
		super(id, name, isVpnConnected);
		this.operatingSystem = operatingSystem;
		this.appVersion = appVersion;
		this.registrationTime = registrationTime;
		this.appLastOpenedDate = appLastOpenedDate;
		this.deviceAnonymizedId = deviceAnonymizedId;
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
		return new UserDeviceDto(deviceEntity.getId(), deviceEntity.getName(),
				deviceEntity.getDeviceAnonymized().getOperatingSystem(), deviceEntity.getDeviceAnonymized().getAppVersion(),
				deviceEntity.isVpnConnected(), deviceEntity.getRegistrationTime(), deviceEntity.getAppLastOpenedDate(),
				deviceEntity.getDeviceAnonymizedId());
	}

	public static UserDeviceDto createDeviceRegistrationInstance(DeviceRegistrationRequestDto deviceRegistration)
	{
		return new UserDeviceDto(deviceRegistration.name,
				parseOperatingSystemOfRegistrationRequest(deviceRegistration.operatingSystemStr), deviceRegistration.appVersion);
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

	public static class DeviceRegistrationRequestDto
	{
		final String name;
		final String operatingSystemStr;
		final String appVersion;

		@JsonCreator
		public DeviceRegistrationRequestDto(@JsonProperty("name") String name,
				@JsonProperty("operatingSystem") String operatingSystemStr, @JsonProperty("appVersion") String appVersion)
		{
			this.name = name;
			this.operatingSystemStr = operatingSystemStr;
			this.appVersion = appVersion;
		}
	}

	public static class DeviceUpdateRequestDto
	{
		final String name;

		@JsonCreator
		public DeviceUpdateRequestDto(@JsonProperty("name") String name)
		{
			this.name = name;
		}
	}
}
