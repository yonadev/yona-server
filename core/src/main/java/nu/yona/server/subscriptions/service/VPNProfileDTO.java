/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.User;

@JsonRootName("vpnProfile")
public class VPNProfileDTO
{
	private final UUID id;
	private final UUID vpnLoginId;
	private String vpnPassword;

	public VPNProfileDTO(UUID id, UUID vpnLoginId, String vpnPassword)
	{
		this.id = id;
		this.vpnLoginId = vpnLoginId;
		this.vpnPassword = vpnPassword;
	}

	@JsonCreator
	public VPNProfileDTO(@JsonProperty("vpnLoginId") UUID vpnLoginId)
	{
		this(null, vpnLoginId, null);
	}

	@JsonIgnore
	public UUID getId()
	{
		return id;
	}

	@JsonProperty("vpnLoginID")
	public UUID getVpnLoginId()
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

	public static VPNProfileDTO createInstance(User user)
	{
		return new VPNProfileDTO(user.getAnonymized().getId(), user.getVpnLoginId(), user.getVpnPassword());
	}
}
