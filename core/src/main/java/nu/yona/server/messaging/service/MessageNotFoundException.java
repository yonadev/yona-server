/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class MessageNotFoundException extends YonaException
{
	private static final long serialVersionUID = -5889584804067081374L;

	private MessageNotFoundException(HttpStatus statusCode, String messageId, Object... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static MessageNotFoundException messageNotFound(long id)
	{
		return new MessageNotFoundException(HttpStatus.NOT_FOUND, "error.message.not.found", id);
	}
}
