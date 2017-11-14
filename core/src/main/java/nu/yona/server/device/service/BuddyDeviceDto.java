/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.device.entities.BuddyDevice;

@JsonRootName("device")
public class BuddyDeviceDto extends DeviceBaseDto
{
	private BuddyDeviceDto(UUID id, String name, boolean isVpnConnected)
	{
		super(id, name, isVpnConnected);
	}

	public static BuddyDeviceDto createInstance(BuddyDevice deviceEntity)
	{
		return new BuddyDeviceDto(deviceEntity.getId(), deviceEntity.getName(), deviceEntity.isVpnConnected());
	}
}
