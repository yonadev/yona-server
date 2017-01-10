/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class WhitelistedNumberServiceException extends YonaException
{
	private static final long serialVersionUID = 2543728671534987461L;

	private WhitelistedNumberServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private WhitelistedNumberServiceException(HttpStatus statusCode, String messageId, Object... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static WhitelistedNumberServiceException numberNotWhitelisted(String mobileNumber)
	{
		return new WhitelistedNumberServiceException(HttpStatus.FORBIDDEN, "error.number.not.whitelisted", mobileNumber);
	}
}
