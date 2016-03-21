/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.entities.NewDeviceRequest;

@JsonRootName("newDeviceRequest")
public class NewDeviceRequestDTO
{
	private String userPassword;
	private boolean isUpdatingExistingRequest;

	public NewDeviceRequestDTO(String userPassword, boolean isUpdatingExistingRequest)
	{
		this.userPassword = userPassword;
		this.isUpdatingExistingRequest = isUpdatingExistingRequest;
	}

	public String getUserPassword()
	{
		return userPassword;
	}

	public boolean getIsUpdatingExistingRequest()
	{
		return isUpdatingExistingRequest;
	}

	public static NewDeviceRequestDTO createInstance(NewDeviceRequest newDeviceRequestEntity, boolean isUpdatingExistingRequest)
	{
		return new NewDeviceRequestDTO(null, isUpdatingExistingRequest);
	}

	public static NewDeviceRequestDTO createInstance(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(null, false);
	}

	public static NewDeviceRequestDTO createInstanceWithPassword(NewDeviceRequest newDeviceRequestEntity)
	{
		return new NewDeviceRequestDTO(newDeviceRequestEntity.getUserPassword(), false);
	}
}
