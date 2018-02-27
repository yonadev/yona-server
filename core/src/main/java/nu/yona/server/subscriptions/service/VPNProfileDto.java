/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@JsonRootName("vpnProfile")
public class VPNProfileDto
{
	private final UUID id;
	private final String vpnLoginId;
	private String vpnPassword;

	public VPNProfileDto(UUID id, String vpnLoginId, String vpnPassword)
	{
		this.id = id;
		this.vpnLoginId = vpnLoginId;
		this.vpnPassword = vpnPassword;
	}

	@JsonIgnore
	public UUID getId()
	{
		return id;
	}

	@JsonProperty("vpnLoginID")
	public String getVpnLoginId()
	{
		return vpnLoginId;
	}

	public String getVpnPassword()
	{
		return vpnPassword;
	}

	public void setVpnPassword(String vpnPassword)
	{
		this.vpnPassword = vpnPassword;
	}

	public static VPNProfileDto createInstance(UserDevice device)
	{
		UserAnonymized userAnonymized = device.getDeviceAnonymized().getUserAnonymized();
		return new VPNProfileDto(userAnonymized.getId(), DeviceService.buildVpnLoginId(userAnonymized, device),
				device.getVpnPassword());
	}
}
