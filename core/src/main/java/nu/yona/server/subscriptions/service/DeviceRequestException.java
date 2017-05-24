/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;

import nu.yona.server.exceptions.YonaException;

public class DeviceRequestException extends YonaException
{
	private static final long serialVersionUID = -7070143633618007280L;

	private DeviceRequestException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	public static DeviceRequestException noDeviceRequestPresent(String mobileNumber)
	{
		return new DeviceRequestException("error.no.device.request.present", mobileNumber);
	}

	public static DeviceRequestException deviceRequestExpired(String mobileNumber)
	{
		return new DeviceRequestException("error.device.request.expired", mobileNumber);
	}

	public static DeviceRequestException invalidNewDeviceRequestPassword(String mobileNumber)
	{
		return new DeviceRequestException("error.device.request.invalid.password", mobileNumber);
	}
}
