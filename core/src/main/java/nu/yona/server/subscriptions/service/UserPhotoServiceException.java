/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;
import java.util.UUID;

import nu.yona.server.exceptions.YonaException;

public class UserPhotoServiceException extends YonaException
{
	private static final long serialVersionUID = -4519219401062670885L;

	private UserPhotoServiceException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	public static UserPhotoServiceException notFoundById(UUID id)
	{
		return new UserPhotoServiceException("error.user.photo.not.found.id", id);
	}
}
