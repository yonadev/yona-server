/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.io.Serializable;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.exceptions.YonaException;

public class DeviceServiceException extends YonaException
{
	private static final long serialVersionUID = 8798601529916186731L;

	private DeviceServiceException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	private DeviceServiceException(HttpStatus statusCode, String messageId, Serializable... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static DeviceServiceException notFoundById(UUID id)
	{
		return new DeviceServiceException(HttpStatus.NOT_FOUND, "error.device.not.found.id", id);
	}

	public static DeviceServiceException notFoundByAnonymizedId(UUID deviceAnonymizedId)
	{
		return new DeviceServiceException(HttpStatus.NOT_FOUND, "error.device.not.found.anonymized.id", "<unknown>",
				deviceAnonymizedId);
	}

	public static DeviceServiceException notFoundByAnonymizedId(UUID userAnonymizedId, UUID deviceAnonymizedId)
	{
		return new DeviceServiceException(HttpStatus.NOT_FOUND, "error.device.not.found.anonymized.id", userAnonymizedId,
				deviceAnonymizedId);
	}

	public static DeviceServiceException cannotDeleteLastDevice(UUID id)
	{
		return new DeviceServiceException("error.device.cannot.delete.last.one", id);
	}

	public static DeviceServiceException noDevicesFound(UUID userAnonymizedId)
	{
		return new DeviceServiceException("error.device.collection.empty", userAnonymizedId);
	}

	public static DeviceServiceException notFoundByIndex(UUID userAnonymizedId, int deviceIndex)
	{
		return new DeviceServiceException("error.device.not.found.index", userAnonymizedId, deviceIndex);
	}

	public static DeviceServiceException duplicateDeviceName(String name)
	{
		return new DeviceServiceException("error.device.name.already.exists", name);
	}

	public static DeviceServiceException appVersionNotSupported(OperatingSystem operatingSystem, String minAppVersion,
			String appVersion)
	{
		return new DeviceServiceException("error.device.app.version.not.supported", operatingSystem, minAppVersion, appVersion);
	}

	public static DeviceServiceException cannotSwitchDeviceOperatingSystem(OperatingSystem currentOperatingSystem,
			OperatingSystem targetOperatingSystem, UUID deviceId)
	{
		return new DeviceServiceException("error.device.cannot.switch.operating.system", currentOperatingSystem,
				targetOperatingSystem, deviceId);
	}

	public static DeviceServiceException invalidVersionCode(int versionCode)
	{
		return new DeviceServiceException("error.device.invalid.version.code", versionCode);
	}
}
