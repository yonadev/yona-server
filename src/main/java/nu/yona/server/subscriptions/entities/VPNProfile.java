/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.crypto.StringFieldEncrypter;
import nu.yona.server.model.EntityWithID;
import nu.yona.server.model.RepositoryProvider;

@Entity
@Table(name = "VPN_PROFILES")
public class VPNProfile extends EntityWithID {
	public static VPNProfileRepository getRepository() {
		return (VPNProfileRepository) RepositoryProvider.getRepository(VPNProfile.class, UUID.class);
	}

	@Convert(converter = StringFieldEncrypter.class)
	private String username;

	// Default constructor is required for JPA
	public VPNProfile() {
		super(null);
	}

	public VPNProfile(UUID id, String username) {
		super(id);
		this.username = username;
	}

	public String getUsername() {
		return username;
	}

	public static VPNProfile createInstance(String username) {
		return new VPNProfile(UUID.randomUUID(), username);
	}
}
