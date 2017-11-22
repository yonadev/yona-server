/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class UserPhotoServiceException extends YonaException
{
	private static final long serialVersionUID = -4519219401062670885L;

	private UserPhotoServiceException(HttpStatus statusCode, String messageId, Serializable... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static UserPhotoServiceException notFoundById(UUID id)
	{
		return new UserPhotoServiceException(HttpStatus.NOT_FOUND, "error.user.photo.not.found.id", id);
	}
}
