/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.google.common.io.Resources;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("vpnProfile")
public class VPNProfileDTO
{
	private final UUID id;
	private UUID vpnLoginID;
	private String vpnPassword;

	public VPNProfileDTO(UUID id, UUID vpnLoginID, String vpnPassword)
	{
		this.id = id;
		this.vpnLoginID = vpnLoginID;
		this.vpnPassword = vpnPassword;
	}

	@JsonCreator
	public VPNProfileDTO(@JsonProperty("vpnLoginID") UUID vpnLoginID)
	{
		this(null, vpnLoginID, null);
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonProperty("vpnLoginID")
	public UUID getVpnLoginID()
	{
		return vpnLoginID;
	}

	public String getVpnPassword()
	{
		return vpnPassword;
	}

	public void setVpnPassword(String vpnPassword)
	{
		this.vpnPassword = vpnPassword;
	}

	public String getOpenVPNProfile()
	{
		try
		{
			URL url = Resources.getResource("OpenVPNProfile.ovpn");
			return Resources.toString(url, StandardCharsets.UTF_8).replace("\n", "\\n");
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static VPNProfileDTO createInstance(User user)
	{
		return new VPNProfileDTO(user.getAnonymized().getID(), user.getVPNLoginID(), user.getVPNPassword());
	}
}
