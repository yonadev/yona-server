/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoUtil
{

	private CryptoUtil()
	{
		// No instances
	}

	public static String getRandomString(int length)
	{
		byte[] bytes = new byte[length * 256 / 64];
		getSecureRandomInstance().nextBytes(bytes);
		String randomString = Base64.getEncoder().encodeToString(bytes);
		return randomString.substring(0, Math.min(length, randomString.length()));
	}

	public static SecureRandom getSecureRandomInstance()
	{
		try
		{
			return SecureRandom.getInstance("SHA1PRNG", "SUN");
		}
		catch (NoSuchAlgorithmException | NoSuchProviderException e)
		{
			throw CryptoException.gettingRandomInstance(e);
		}
	}
}
