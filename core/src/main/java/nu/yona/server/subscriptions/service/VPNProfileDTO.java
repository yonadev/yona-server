/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.subscriptions.entities.VPNProfile;

public class VPNProfileDTO {
	private final UUID id;
	private String username;

	public VPNProfileDTO(UUID id, String username) {
		this.id = id;
		this.username = username;
	}

	@JsonCreator
	public VPNProfileDTO(@JsonProperty("username") String username) {
		this(null, username);
	}

	@JsonIgnore
	public UUID getID() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public static VPNProfileDTO createInstance(VPNProfile vpnProfileEntity) {
		return new VPNProfileDTO(vpnProfileEntity.getID(), vpnProfileEntity.getUsername());
	}
}
