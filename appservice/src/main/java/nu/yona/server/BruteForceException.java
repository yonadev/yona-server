/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.net.URI;

import nu.yona.server.exceptions.YonaException;

public class BruteForceException extends YonaException
{
	private static final long serialVersionUID = 2837321499539339643L;

	protected BruteForceException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static BruteForceException tooManyAttempts(URI uri)
	{
		return new BruteForceException("error.too.many.wrong.attempts", uri);
	}
}
