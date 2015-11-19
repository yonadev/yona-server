/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

public class YonaException extends RuntimeException
{

	private static final long serialVersionUID = 6332689175661269736L;

	public YonaException(String message)
	{
		super(message);
	}

	public YonaException(Throwable cause)
	{
		super(cause);
	}

	public YonaException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
