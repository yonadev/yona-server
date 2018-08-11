/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;

public class AppOpenEventDto
{
	public final String operatingSystemStr;
	public String appVersion;
	public final int appVersionCode;

	@JsonCreator
	public AppOpenEventDto(@JsonProperty("operatingSystem") String operatingSystemStr,
			@JsonProperty("appVersion") String appVersion, @JsonProperty("appVersionCode") Integer appVersionCode)
	{
		this.operatingSystemStr = operatingSystemStr;
		this.appVersion = appVersion;
		this.appVersionCode = (appVersionCode == null) ? 0 : appVersionCode;
	}

	public Optional<OperatingSystem> getOperatingSystem()
	{
		return operatingSystemStr == null ? Optional.empty()
				: Optional.of(UserDeviceDto.parseOperatingSystemOfRegistrationRequest(operatingSystemStr));
	}
}