/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.NewDeviceRequest;

@JsonRootName("newDeviceRequest")
public class NewDeviceRequestDto
{
	private String yonaPassword;

	public NewDeviceRequestDto(String yonaPassword)
	{
		this.yonaPassword = yonaPassword;
	}

	public String getYonaPassword()
	{
		return yonaPassword;
	}

	public static NewDeviceRequestDto createInstance(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDto(null);
	}

	public static NewDeviceRequestDto createInstanceWithPassword(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDto(newDeviceRequestEntity.getYonaPassword());
	}
}
