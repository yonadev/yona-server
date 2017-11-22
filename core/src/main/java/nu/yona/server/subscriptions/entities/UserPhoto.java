/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithUuid;

@Entity
@Table(name = "USER_PHOTO")
public class UserPhoto extends EntityWithUuid
{
	@Column(length = 128000)
	private byte[] pngBytes;

	// Default constructor is required for JPA
	public UserPhoto()
	{
		super(null);
	}

	private UserPhoto(UUID id, byte[] pngBytes)
	{
		super(id);
		this.pngBytes = pngBytes;
	}

	public static UserPhoto createInstance(byte[] pngBytes)
	{
		return new UserPhoto(UUID.randomUUID(), pngBytes);
	}

	public byte[] getPngBytes()
	{
		return pngBytes;
	}
}
