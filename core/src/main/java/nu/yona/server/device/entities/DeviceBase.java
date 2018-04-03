/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.entities;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;

import nu.yona.server.crypto.seckey.StringFieldEncryptor;
import nu.yona.server.crypto.seckey.UUIDFieldEncryptor;
import nu.yona.server.subscriptions.entities.EntityWithUuidAndTouchVersion;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class DeviceBase extends EntityWithUuidAndTouchVersion
{
	public static final int MAX_NAME_LENGTH = 20;
	public static final String DEVICE_NAMES_SEPARATOR = ":";

	@Convert(converter = StringFieldEncryptor.class)
	private String name;

	@Convert(converter = UUIDFieldEncryptor.class)
	private UUID deviceAnonymizedId;

	// Default constructor is required for JPA
	protected DeviceBase()
	{
		super(null);
	}

	protected DeviceBase(UUID id, String name, UUID deviceAnonymizedId)
	{
		super(id);
		this.name = Objects.requireNonNull(name);
		this.deviceAnonymizedId = Objects.requireNonNull(deviceAnonymizedId);
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public boolean isVpnConnected()
	{
		return getDeviceAnonymizedIfExisting().flatMap(DeviceAnonymized::getLastVpnStatusChangeEvent)
				.map(VpnStatusChangeEvent::isVpnConnected).orElse(false);
	}

	public UUID getDeviceAnonymizedId()
	{
		return deviceAnonymizedId;
	}

	public DeviceAnonymized getDeviceAnonymized()
	{
		return getDeviceAnonymizedIfExisting()
				.orElseThrow(() -> new IllegalStateException("DeviceAnonymized with ID " + deviceAnonymizedId + " not found"));
	}

	private Optional<DeviceAnonymized> getDeviceAnonymizedIfExisting()
	{
		return Optional.ofNullable(DeviceAnonymized.getRepository().findOne(deviceAnonymizedId));
	}
}
