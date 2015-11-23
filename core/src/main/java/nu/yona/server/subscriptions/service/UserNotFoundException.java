/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import nu.yona.server.exceptions.YonaException;

public class UserNotFoundException extends YonaException
{
	private static final long serialVersionUID = -4519219401062670885L;

	private UserNotFoundException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static UserNotFoundException notFoundByMobileNumber(String mobileNumber)
	{
		return new UserNotFoundException("error.user.not.found.mobile", mobileNumber);
	}

	public static UserNotFoundException notFoundByID(UUID id)
	{
		return new UserNotFoundException("error.user.not.found.mobile", id);
	}
}
