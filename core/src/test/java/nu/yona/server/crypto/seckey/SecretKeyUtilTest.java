/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class SecretKeyUtilTest
{
	private static final String PASSWORD1 = "secret";

	@Test
	public void encryptUuid_default_returnsCiphertext()
	{
		UUID uuid = UUID.randomUUID();
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(PASSWORD1), () -> true))
		{
			CryptoSession.getCurrent().generateInitializationVector();

			byte[] result = SecretKeyUtil.encryptUuid(uuid);

			assertThat(result.length, greaterThan(16));
		}
	}

	@Test
	public void decryptUuid_encryptedUuid_returnsOriginal()
	{
		UUID uuid = UUID.randomUUID();
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(PASSWORD1), () -> true))
		{
			CryptoSession.getCurrent().generateInitializationVector();
			byte[] ciphertext = SecretKeyUtil.encryptUuid(uuid);

			UUID result = SecretKeyUtil.decryptUuid(ciphertext);

			assertThat(result, equalTo(uuid));
		}
	}
}
