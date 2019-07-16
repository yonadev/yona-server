/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceRegistrationRequestDto extends AppOpenEventDto
{
	public final String name;
	public final Optional<String> firebaseInstanceId;

	@JsonCreator
	public DeviceRegistrationRequestDto(@JsonProperty("name") String name,
			@JsonProperty("operatingSystem") String operatingSystemStr, @JsonProperty("appVersion") String appVersion,
			@JsonProperty("appVersionCode") int appVersionCode,
			@JsonProperty("firebaseInstanceId") Optional<String> firebaseInstanceId)
	{
		super(operatingSystemStr, appVersion, appVersionCode);
		this.name = name;
		this.firebaseInstanceId = firebaseInstanceId;
	}
}