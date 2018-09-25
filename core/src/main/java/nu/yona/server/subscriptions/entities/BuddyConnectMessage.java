/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.lang.StringUtils;

import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.device.entities.DeviceBase;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.rest.RestUtil;

@Entity
public abstract class BuddyConnectMessage extends BuddyConnectionChangeMessage
{
	private static final List<String> EMPTY_STRING_LIST = Collections.emptyList();
	private static final String SEPARATOR = DeviceBase.DEVICE_NAMES_SEPARATOR;
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
		List<UserDevice> orderedDevices = new ArrayList<>(devices);
		this.deviceNames = buildDeviceNamesString(orderedDevices);
		this.deviceAnonymizedIds = buildDeviceAnonymizedIdsString(orderedDevices);
		this.deviceVpnConnectionStatuses = buildDeviceVpnConnectionStatusesString(orderedDevices);
	}

	private String buildDeviceNamesString(List<UserDevice> devices)
	{
		return devices.stream().map(UserDevice::getName).collect(Collectors.joining(SEPARATOR));
	}

	private String buildDeviceAnonymizedIdsString(List<UserDevice> devices)
	{
		return devices.stream().map(UserDevice::getDeviceAnonymizedId).map(UUID::toString).collect(Collectors.joining(SEPARATOR));
	}

	private String buildDeviceVpnConnectionStatusesString(List<UserDevice> devices)
	{
		return devices.stream().map(d -> Boolean.toString(d.isVpnConnected())).collect(Collectors.joining(SEPARATOR));
	}

	public UUID getBuddyId()
	{
		return buddyId;
	}

	public List<String> getDeviceNames()
	{
		return splitString(deviceNames).collect(Collectors.toList());
	}

	public List<UUID> getDeviceAnonymizedIds()
	{
		return splitString(deviceAnonymizedIds).map(RestUtil::parseUuid).collect(Collectors.toList());
	}

	public List<Boolean> getDeviceVpnConnectionStatuses()
	{
		return splitString(deviceVpnConnectionStatuses).map(Boolean::parseBoolean).collect(Collectors.toList());
	}

	private static Stream<String> splitString(String value)
	{
		return StringUtils.isBlank(value) ? EMPTY_STRING_LIST.stream() : Arrays.stream(value.split(SEPARATOR));
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
