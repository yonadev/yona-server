/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

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
	private final LocalDate appLastOpenedDate;

	public UserDeviceDto(String name, OperatingSystem operatingSystem)
	{
		this(null, name, operatingSystem, true, LocalDateTime.now(), LocalDate.now());
	}

	private UserDeviceDto(UUID id, String name, OperatingSystem operatingSystem, boolean isVpnConnected,
			LocalDateTime registrationTime, LocalDate appLastOpenedDate)
	{
		super(id, name, isVpnConnected);
		this.operatingSystem = operatingSystem;
		this.registrationTime = registrationTime;
		this.appLastOpenedDate = appLastOpenedDate;
	}

	public OperatingSystem getOperatingSystem()
	{
		return operatingSystem;
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

	public static UserDeviceDto createInstance(UserDevice deviceEntity)
	{
		return new UserDeviceDto(deviceEntity.getId(), deviceEntity.getName(),
				deviceEntity.getDeviceAnonymized().getOperatingSystem(), deviceEntity.isVpnConnected(),
				deviceEntity.getRegistrationTime(), deviceEntity.getAppLastOpenedDate());
	}

	public static UserDeviceDto createPostPutInstance(String name, String operatingSystemStr)
	{
		return new UserDeviceDto(name, parsePostPutOperatingSystem(operatingSystemStr));
	}

	private static OperatingSystem parsePostPutOperatingSystem(String operatingSystemStr)
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
}
