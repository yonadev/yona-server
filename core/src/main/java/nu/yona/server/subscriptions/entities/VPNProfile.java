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

import nu.yona.server.crypto.UUIDFieldEncrypter;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "VPN_PROFILES")
public class VPNProfile extends EntityWithID {
	public static VPNProfileRepository getRepository() {
		return (VPNProfileRepository) RepositoryProvider.getRepository(VPNProfile.class, UUID.class);
	}

	@Convert(converter = UUIDFieldEncrypter.class)
	private UUID loginID;

	// Default constructor is required for JPA
	public VPNProfile() {
		super(null);
	}

	public VPNProfile(UUID id, UUID loginID) {
		super(id);
		this.loginID = loginID;
	}

	public UUID getLoginID() {
		return loginID;
	}

	public static VPNProfile createInstance(UUID loginID) {
		return new VPNProfile(UUID.randomUUID(), loginID);
	}
}
