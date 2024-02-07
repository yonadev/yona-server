/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;
import org.hibernate.type.descriptor.jdbc.TinyIntJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.device.service.DeviceChange;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public class BuddyDeviceChangeMessage extends BuddyMessage
{
	@JdbcType(value = IntegerJdbcType.class)
	@Column(name = "device_change")
	private DeviceChange change;

	@Transient
	private UUID deviceAnonymizedId;
	private byte[] deviceAnonymizedIdCiphertext;

	@Transient
	private Optional<String> oldName;
	private byte[] oldNameCiphertext;

	@Transient
	private Optional<String> newName;
	private byte[] newNameCiphertext;

	@JdbcType(value = TinyIntJdbcType.class)
	private boolean isProcessed;

	// Default constructor is required for JPA
	public BuddyDeviceChangeMessage()
	{
		super();
	}

	private BuddyDeviceChangeMessage(BuddyInfoParameters buddyInfoParameters, String messageText, DeviceChange change,
			UUID deviceAnonymizedId, Optional<String> oldName, Optional<String> newName)
	{
		super(buddyInfoParameters, messageText);
		this.change = change;
		this.deviceAnonymizedId = deviceAnonymizedId;
		this.oldName = oldName;
		this.newName = newName;
	}

	public static BuddyDeviceChangeMessage createInstance(BuddyInfoParameters buddyInfoParameters, String messageText,
			DeviceChange change, UUID deviceAnonymizedId, Optional<String> oldName, Optional<String> newName)
	{
		return new BuddyDeviceChangeMessage(buddyInfoParameters, messageText, change, deviceAnonymizedId, oldName, newName);
	}

	public DeviceChange getChange()
	{
		return change;
	}

	public UUID getDeviceAnonymizedId()
	{
		return deviceAnonymizedId;
	}

	public Optional<String> getOldName()
	{
		return oldName;
	}

	public Optional<String> getNewName()
	{
		return newName;
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
		oldNameCiphertext = SecretKeyUtil.encryptString(getOldName().orElse(null));
		newNameCiphertext = SecretKeyUtil.encryptString(getNewName().orElse(null));
		deviceAnonymizedIdCiphertext = SecretKeyUtil.encryptUuid(getDeviceAnonymizedId());
	}

	@Override
	public void decrypt()
	{
		super.decrypt();
		this.oldName = Optional.ofNullable(SecretKeyUtil.decryptString(oldNameCiphertext));
		this.newName = Optional.ofNullable(SecretKeyUtil.decryptString(newNameCiphertext));
		this.deviceAnonymizedId = SecretKeyUtil.decryptUuid(deviceAnonymizedIdCiphertext);
	}
}
