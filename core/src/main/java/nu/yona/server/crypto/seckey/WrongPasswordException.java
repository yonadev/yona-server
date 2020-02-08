/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;

import java.io.Serializable;

import nu.yona.server.exceptions.YonaException;

public class WrongPasswordException extends YonaException
{
	private static final long serialVersionUID = 1989876591001478378L;

	private WrongPasswordException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	public static WrongPasswordException passwordHeaderNotProvided()
	{
		return new WrongPasswordException("error.missing.password.header", PASSWORD_HEADER);
	}

	public static WrongPasswordException wrongPasswordHeaderProvided(String marker, int expectedBytes, int actualBytes)
	{
		return new WrongPasswordException("error.wrong.password.header", PASSWORD_HEADER, marker, expectedBytes, actualBytes);
	}
}
