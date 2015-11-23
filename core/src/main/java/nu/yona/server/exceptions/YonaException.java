/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

/**
 * Generic exception class for any Yona exception.
 * 
 * @author pgussow
 */
public class YonaException extends ResourceBasedException
{
	private static final long serialVersionUID = 6332689175661269736L;

	public YonaException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public YonaException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}
}
