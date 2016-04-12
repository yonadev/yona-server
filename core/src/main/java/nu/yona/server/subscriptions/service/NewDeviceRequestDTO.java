/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.NewDeviceRequest;

@JsonRootName("newDeviceRequest")
public class NewDeviceRequestDTO
{
	private String yonaPassword;

	public NewDeviceRequestDTO(String yonaPassword)
	{
		this.yonaPassword = yonaPassword;
	}

	public String getYonaPassword()
	{
		return yonaPassword;
	}

	public static NewDeviceRequestDTO createInstance(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(null);
	}

	public static NewDeviceRequestDTO createInstanceWithPassword(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getYonaPassword());
	}
}
