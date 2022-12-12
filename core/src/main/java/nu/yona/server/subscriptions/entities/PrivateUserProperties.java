/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;

@MappedSuperclass
public abstract class PrivateUserProperties extends EntityWithUuidAndTouchVersion
{

	@Convert(converter = StringFieldEncryptor.class)
	private String firstName;

	@Convert(converter = StringFieldEncryptor.class)
	private String lastName;

	@Convert(converter = StringFieldEncryptor.class)
	private String nickname;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID userPhotoId;

	// Default constructor is required for JPA
	protected PrivateUserProperties()
	{
		super(null);
	}

	protected PrivateUserProperties(UUID id, String firstName, String lastName, String nickname, Optional<UUID> userPhotoId)
	{
		super(id);
		this.firstName = Objects.requireNonNull(firstName);
		this.lastName = Objects.requireNonNull(lastName);
		this.nickname = nickname;
		this.userPhotoId = userPhotoId.orElse(null);
	}

	public String getFirstName()
	{
		return firstName;
	}

	public void setFirstName(String firstName)
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public void setLastName(String lastName)
	{
		this.lastName = lastName;
	}

	public String getNickname()
	{
		return nickname;
	}

	public void setNickname(String nickname)
	{
		this.nickname = nickname;
	}

	public Optional<UUID> getUserPhotoId()
	{
		return Optional.ofNullable(userPhotoId);
	}

	public void setUserPhotoId(Optional<UUID> userPhotoId)
	{
		this.userPhotoId = userPhotoId.orElse(null);
	}
}