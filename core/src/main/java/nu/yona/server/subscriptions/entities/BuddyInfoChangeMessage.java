/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Optional;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public class BuddyInfoChangeMessage extends BuddyMessage
{
	@Transient
	private String newNickname;
	private byte[] newNicknameCiphertext;
	@Transient
	private Optional<UUID> newUserPhotoId;
	private byte[] newUserPhotoIdCiphertext;
	private boolean isProcessed;

	public BuddyInfoChangeMessage(BuddyInfoParameters buddyInfoParameters, String message, String newNickname,
			Optional<UUID> newUserPhotoId)
	{
		super(buddyInfoParameters, message);
		this.newNickname = newNickname;
		this.newUserPhotoId = newUserPhotoId;
	}

	// Default constructor is required for JPA
	public BuddyInfoChangeMessage()
	{
		super();
	}

	public static BuddyInfoChangeMessage createInstance(BuddyInfoParameters buddyInfoParameters, String message,
			String newNickname, Optional<UUID> newUserPhotoId)
	{
		return new BuddyInfoChangeMessage(buddyInfoParameters, message, newNickname, newUserPhotoId);
	}

	public String getNewNickname()
	{
		return newNickname;
	}

	public Optional<UUID> getNewUserPhotoId()
	{
		return newUserPhotoId;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	public void setProcessed()
	{
		this.isProcessed = true;
	}

	@Override
	public void encrypt()
	{
		super.encrypt();
		newNicknameCiphertext = SecretKeyUtil.encryptString(newNickname);
		newUserPhotoIdCiphertext = SecretKeyUtil.encryptUuid(newUserPhotoId.orElse(null));
	}

	@Override
	public void decrypt()
	{
		super.decrypt();
		newNickname = SecretKeyUtil.decryptString(newNicknameCiphertext);
		newUserPhotoId = Optional.ofNullable(SecretKeyUtil.decryptUuid(newUserPhotoIdCiphertext));
	}
}
