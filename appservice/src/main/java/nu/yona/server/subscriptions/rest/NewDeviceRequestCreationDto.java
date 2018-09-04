/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("newDeviceCreationRequest")
public class NewDeviceRequestCreationDto
{
	private final String newDeviceRequestPassword;

	@JsonCreator
	public NewDeviceRequestCreationDto(@JsonProperty("newDeviceRequestPassword") String newDeviceRequestPassword)
	{
		this.newDeviceRequestPassword = newDeviceRequestPassword;
	}

	public String getNewDeviceRequestPassword()
	{
		return newDeviceRequestPassword;
	}
}
