/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.UserAnonymized;

@JsonRootName("vpnProfile")
public class VPNProfileDTO
{
	private final UUID id;
	private UUID loginID;

	public VPNProfileDTO(UUID id, UUID loginID)
	{
		this.id = id;
		this.loginID = loginID;
	}

	@JsonCreator
	public VPNProfileDTO(@JsonProperty("loginID") UUID loginID)
	{
		this(null, loginID);
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	public UUID getLoginID()
	{
		return loginID;
	}

	public static VPNProfileDTO createInstance(UserAnonymized userAnonymized)
	{
		return new VPNProfileDTO(userAnonymized.getID(), userAnonymized.getLoginID());
	}
}
