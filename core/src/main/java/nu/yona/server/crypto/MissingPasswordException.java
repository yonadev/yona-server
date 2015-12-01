/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import nu.yona.server.exceptions.YonaException;

public class MissingPasswordException extends YonaException
{
	private static final long serialVersionUID = 1989876591001478378L;

	private MissingPasswordException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static MissingPasswordException passwordHeaderNotProvided()
	{
		return new MissingPasswordException("error.missing.password.header", Constants.PASSWORD_HEADER);
	}
}
