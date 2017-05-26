/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;
import java.util.UUID;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.rest.Constants;

public class UserServiceException extends YonaException
{
	private static final long serialVersionUID = -4519219401062670885L;

	private UserServiceException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	public static UserServiceException notFoundByMobileNumber(String mobileNumber)
	{
		return new UserServiceException("error.user.not.found.mobile", mobileNumber);
	}

	public static UserServiceException notFoundById(UUID id)
	{
		return new UserServiceException("error.user.not.found.id", id);
	}

	public static UserServiceException missingPasswordHeader()
	{
		return new UserServiceException("error.missing.password.header", Constants.PASSWORD_HEADER);
	}

	public static UserServiceException userExistsCreatedOnBuddyRequest(String mobileNumber)
	{
		return new UserServiceException("error.user.exists.created.on.buddy.request", mobileNumber);
	}

	public static UserServiceException userExists(String mobileNumber)
	{
		return new UserServiceException("error.user.exists", mobileNumber);
	}

	public static UserServiceException userCreatedOnBuddyRequest(UUID id)
	{
		return new UserServiceException("error.user.created.on.buddy.request", id);
	}

	public static UserServiceException userNotCreatedOnBuddyRequest(UUID id)
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

	public static UserServiceException maximumNumberOfUsersReached()
	{
		return new UserServiceException("error.user.maximum.number.reached");
	}
}
