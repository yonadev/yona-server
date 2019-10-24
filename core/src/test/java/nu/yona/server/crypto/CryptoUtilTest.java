/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class CryptoUtilTest
{
	@Test
	public void getRandomString_tryTenStrings_onlyValidUrlCharactersUsed()
	{
		for (int i = 0; (i < 10); i++)
		{
			String randomString = CryptoUtil.getRandomString(50);
			assertThat(URLEncoder.encode(randomString, StandardCharsets.UTF_8), equalTo(randomString));
		}
	}
}
