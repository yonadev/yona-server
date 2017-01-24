/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class WhiteListedNumberServiceException extends YonaException
{
	private static final long serialVersionUID = 2543728671534987461L;

	private WhiteListedNumberServiceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private WhiteListedNumberServiceException(HttpStatus statusCode, String messageId, Object... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static WhiteListedNumberServiceException numberNotWhiteListed(String mobileNumber)
	{
		return new WhiteListedNumberServiceException(HttpStatus.BAD_REQUEST, "error.number.not.white.listed", mobileNumber);
	}
}
