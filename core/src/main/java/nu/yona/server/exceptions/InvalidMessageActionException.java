/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

/**
 * This exception is to be used when an action is called for a message which is not supported.
 */
public class InvalidMessageActionException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private InvalidMessageActionException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private InvalidMessageActionException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static InvalidMessageActionException unprocessedMessageCannotBeDeleted()
	{
		return new InvalidMessageActionException("error.cannot.delete.unprocessed.message");
	}
}
