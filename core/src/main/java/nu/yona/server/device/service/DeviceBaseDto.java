/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class DeviceBaseDto
{
	private final UUID id;
	private final String name;
	private final boolean isVpnConnected;

	protected DeviceBaseDto(UUID id, String name, boolean isVpnConnected)
	{
		this.id = id;
		this.name = name;
		this.isVpnConnected = isVpnConnected;
	}

	@JsonIgnore
	public UUID getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public boolean isVpnConnected()
	{
		return isVpnConnected;
	}
}
