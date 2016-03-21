/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
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
import nu.yona.server.entities.EntityWithID;

@Entity
@Table(name = "DEVICES")
public class Device extends EntityWithID
{
	@Convert(converter = StringFieldEncrypter.class)
	private String name;

	// Default constructor is required for JPA
	public Device()
	{
		super(null);
	}

	private Device(UUID id, String name)
	{
		super(id);
		this.name = name;
	}

	public static Device createInstance(String name)
	{
		return new Device(UUID.randomUUID(), name);
	}

	public String getName()
	{
		return name;
	}
}
