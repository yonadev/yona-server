/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.NewDeviceRequest;

@JsonRootName("newDeviceRequest")
public class NewDeviceRequestDto
{
	private final Optional<String> yonaPassword;

	public NewDeviceRequestDto(String yonaPassword)
	{
		this.yonaPassword = Optional.of(yonaPassword);
	}

	public NewDeviceRequestDto()
	{
		this.yonaPassword = Optional.empty();
	}

	public Optional<String> getYonaPassword()
	{
		return yonaPassword;
	}

	public static NewDeviceRequestDto createInstanceWithoutPassword()
	{
		return new NewDeviceRequestDto();
	}

	public static NewDeviceRequestDto createInstanceWithPassword(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDto(newDeviceRequestEntity.getYonaPassword());
	}
}
