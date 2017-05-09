/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import nu.yona.server.exceptions.YonaException;

public class BuddyNotFoundException extends YonaException
{
	private static final long serialVersionUID = -5211242495003355230L;

	private BuddyNotFoundException(HttpStatus statusCode, String messageId, Object... parameters)
	{
		super(statusCode, messageId, parameters);
	}

	public static BuddyNotFoundException notFound(UUID id)
	{
		return new BuddyNotFoundException(HttpStatus.NOT_FOUND, "error.buddy.not.found", id);
	}
}
