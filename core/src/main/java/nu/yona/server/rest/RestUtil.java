/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.util.UUID;

import nu.yona.server.exceptions.InvalidDataException;

public class RestUtil
{
	private RestUtil()
	{
		// No instances
	}

	public static UUID parseUuid(String uuid)
	{
		try
		{
			return UUID.fromString(uuid);
		}
		catch (IllegalArgumentException e)
		{
			throw InvalidDataException.invalidUuid(e, uuid);
		}
	}
}