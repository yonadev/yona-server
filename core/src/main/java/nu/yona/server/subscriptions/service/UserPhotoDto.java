/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.UserPhoto;

@JsonRootName("userPhoto")
public class UserPhotoDto
{
	private final byte[] pngBytes;
	private final Optional<UUID> id;

	private UserPhotoDto(Optional<UUID> id, byte[] pngBytes)
	{
		this.id = id;
		this.pngBytes = pngBytes;
	}

	@JsonIgnore
	public Optional<UUID> getId()
	{
		return id;
	}

	@JsonIgnore
	public byte[] getPngBytes()
	{
		return pngBytes;
	}

	public static UserPhotoDto createInstance(byte[] pngBytes)
	{
		return new UserPhotoDto(Optional.empty(), pngBytes);
	}

	static UserPhotoDto createInstance(UserPhoto entity)
	{
		return new UserPhotoDto(Optional.of(entity.getId()), entity.getPngBytes());
	}
}
