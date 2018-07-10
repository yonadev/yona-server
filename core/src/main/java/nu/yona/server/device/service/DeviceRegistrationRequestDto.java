/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceRegistrationRequestDto
{
	final String name;
	final String operatingSystemStr;
	final String appVersion;
	final Optional<String> firebaseInstanceId;

	@JsonCreator
	public DeviceRegistrationRequestDto(@JsonProperty("name") String name,
			@JsonProperty("operatingSystem") String operatingSystemStr, @JsonProperty("appVersion") String appVersion,
			@JsonProperty("firebaseInstanceId") Optional<String> firebaseInstanceId)
	{
		this.name = name;
		this.operatingSystemStr = operatingSystemStr;
		this.appVersion = appVersion;
		this.firebaseInstanceId = firebaseInstanceId;
	}
}