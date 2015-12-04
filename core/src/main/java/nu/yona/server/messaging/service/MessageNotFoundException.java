/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class MessageNotFoundException extends YonaException
{
	private static final long serialVersionUID = -5889584804067081374L;

	private MessageNotFoundException(HttpStatus status, String messageId, Object... parameters)
	{
		super(messageId, parameters);
		setStatusCode(status);
	}

	public static MessageNotFoundException messageNotFound(UUID id)
	{
		return new MessageNotFoundException(HttpStatus.NOT_FOUND, "error.message.not.found", id);
	}
}
