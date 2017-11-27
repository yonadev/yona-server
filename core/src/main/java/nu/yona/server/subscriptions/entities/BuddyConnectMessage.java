/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.device.entities.DeviceBase;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.messaging.entities.BuddyMessage;

@Entity
public abstract class BuddyConnectMessage extends BuddyMessage
{
	private static final int UUID_LENGTH = 36;
	private static final int BOOLEAN_LENGTH = 5;
	private static final int MAX_NUM_OF_DEVICES = 10;
	private static final double ENCRYPTION_OVERHEAD_FACTOR = 1.2; // This is a guess

	@Transient
	private UUID buddyId;
	private byte[] buddyIdCiphertext;

	@Transient
	private String deviceNames;
	@Column(length = (int) ((DeviceBase.MAX_NAME_LENGTH + 1) * MAX_NUM_OF_DEVICES * ENCRYPTION_OVERHEAD_FACTOR))
	private byte[] deviceNamesCiphertext;

	@Transient
	private String deviceAnonymizedIds;
	@Column(length = (int) ((UUID_LENGTH + 1) * MAX_NUM_OF_DEVICES * ENCRYPTION_OVERHEAD_FACTOR))
	private byte[] deviceAnonymizedIdsCiphertext;

	@Transient
	private String deviceVpnConnectionStatuses;
	@Column(length = (int) ((BOOLEAN_LENGTH + 1) * MAX_NUM_OF_DEVICES * ENCRYPTION_OVERHEAD_FACTOR))
	private byte[] deviceVpnConnectionStatusesCiphertext;

	// Default constructor is required for JPA
	protected BuddyConnectMessage()
	{
		super();
	}

	protected BuddyConnectMessage(BuddyInfoParameters buddyInfoParameters, String message, UUID buddyId, Set<UserDevice> devices)
	{
		super(buddyInfoParameters, message);
		this.buddyId = buddyId;
		List<UserDevice> sortedDevices = devices.stream()
				.sorted((d1, d2) -> d1.getDeviceAnonymizedId().compareTo(d2.getDeviceAnonymizedId()))
				.collect(Collectors.toList());
		this.deviceNames = buildDeviceNamesString(sortedDevices);
		this.deviceAnonymizedIds = buildDeviceAnonymizedIdsString(sortedDevices);
		this.deviceVpnConnectionStatuses = buildDeviceVpnConnectionStatusesString(sortedDevices);
	}

	private String buildDeviceNamesString(List<UserDevice> devices)
	{
		return devices.stream().map(UserDevice::getName).collect(Collectors.joining(DeviceBase.DEVICE_NAMES_SEPARATOR));
	}

	private String buildDeviceAnonymizedIdsString(List<UserDevice> devices)
	{
		return devices.stream().map(UserDevice::getDeviceAnonymizedId).map(UUID::toString)
				.collect(Collectors.joining(DeviceBase.DEVICE_NAMES_SEPARATOR));
	}

	private String buildDeviceVpnConnectionStatusesString(List<UserDevice> devices)
	{
		return devices.stream().map(d -> Boolean.toString(d.isVpnConnected()))
				.collect(Collectors.joining(DeviceBase.DEVICE_NAMES_SEPARATOR));
	}

	public UUID getBuddyId()
	{
		return buddyId;
	}

	public List<String> getDeviceNames()
	{
		return Arrays.stream(deviceNames.split(DeviceBase.DEVICE_NAMES_SEPARATOR)).collect(Collectors.toList());
	}

	public List<UUID> getDeviceAnonymizedIds()
	{
		return Arrays.stream(deviceAnonymizedIds.split(DeviceBase.DEVICE_NAMES_SEPARATOR)).map(UUID::fromString)
				.collect(Collectors.toList());
	}

	public List<Boolean> getDeviceVpnConnectionStatuses()
	{
		return Arrays.stream(deviceVpnConnectionStatuses.split(DeviceBase.DEVICE_NAMES_SEPARATOR)).map(Boolean::parseBoolean)
				.collect(Collectors.toList());
	}

	@Override
	public void encrypt()
	{
		super.encrypt();
		buddyIdCiphertext = SecretKeyUtil.encryptUuid(buddyId);
		deviceNamesCiphertext = SecretKeyUtil.encryptString(deviceNames);
		deviceAnonymizedIdsCiphertext = SecretKeyUtil.encryptString(deviceAnonymizedIds);
		deviceVpnConnectionStatusesCiphertext = SecretKeyUtil.encryptString(deviceVpnConnectionStatuses);
	}

	@Override
	public void decrypt()
	{
		super.decrypt();
		buddyId = SecretKeyUtil.decryptUuid(buddyIdCiphertext);
		deviceNames = SecretKeyUtil.decryptString(deviceNamesCiphertext);
		deviceAnonymizedIds = SecretKeyUtil.decryptString(deviceAnonymizedIdsCiphertext);
		deviceVpnConnectionStatuses = SecretKeyUtil.decryptString(deviceVpnConnectionStatusesCiphertext);
	}
}
