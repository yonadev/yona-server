/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

/**
 * This exception is thrown for various configuration issues
 */
public class ConfigurationException extends RuntimeException
{
	private static final long serialVersionUID = 2564982200833119780L;

	private ConfigurationException(String message)
	{
		super(message);
	}

	public static ConfigurationException missingKeyInKeyStore(String alias)
	{
		return new ConfigurationException("Missing key '" + alias + "' in key store");
	}
}
