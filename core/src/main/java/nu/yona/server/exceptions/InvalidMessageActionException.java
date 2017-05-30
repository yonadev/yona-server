/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.io.Serializable;

/**
 * This exception is to be used when an action is called for a message which is not supported.
 */
public class InvalidMessageActionException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private InvalidMessageActionException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	public static InvalidMessageActionException unprocessedMessageCannotBeDeleted()
	{
		return new InvalidMessageActionException("error.cannot.delete.unprocessed.message");
	}
}
