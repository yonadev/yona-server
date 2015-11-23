/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import nu.yona.server.exceptions.YonaException;

public class DecryptionException extends YonaException
{
	private static final long serialVersionUID = -8007474934276158682L;

	private DecryptionException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	public static DecryptionException decryptingData()
	{
		return new DecryptionException("error.decrypting.data");
	}
}
