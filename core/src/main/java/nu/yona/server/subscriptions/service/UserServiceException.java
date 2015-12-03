/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import nu.yona.server.crypto.Constants;
import nu.yona.server.exceptions.YonaException;

public class UserServiceException extends YonaException
{
	private static final long serialVersionUID = -4519219401062670885L;

	private UserServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static UserServiceException notFoundByMobileNumber(String mobileNumber)
	{
		return new UserServiceException("error.user.not.found.mobile", mobileNumber);
	}

	public static UserServiceException notFoundByID(UUID id)
	{
		return new UserServiceException("error.user.not.found.id", id);
	}

	public static UserServiceException missingPasswordHeader()
	{
		return new UserServiceException("error.missing.password.header", Constants.PASSWORD_HEADER);
	}

	public static UserServiceException usernotCreatedOnBuddyRequest(UUID id)
	{
		return new UserServiceException("error.user.not.created.on.buddy.request", id);
	}

	public static UserServiceException cannotUpdateBecauseCreatedOnBuddyRequest(UUID id)
	{
		return new UserServiceException("error.user.cannot.update.because.created.on.buddy.request", id);
	}

	public static UserServiceException missingOrNullUser()
	{
		return new UserServiceException("error.user.missing.or.null");
	}

	public static UserServiceException missingMobileNumber()
	{
		return new UserServiceException("error.user.missing.mobile.number");
	}
}
