/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test.util;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import nu.yona.server.crypto.seckey.CryptoSession;

/**
 * This JUnit rule executes each test inside a CryptoSession with the given password.
 */
public class CryptoSessionRule implements MethodRule
{
	private String password;

	public CryptoSessionRule(String password)
	{
		this.password = password;
	}

	@Override
	public Statement apply(Statement base, FrameworkMethod method, Object target)
	{
		return new Statement() {
			@Override
			public void evaluate() throws Throwable
			{
				try (CryptoSession cryptoSession = CryptoSession.start(password))
				{
					base.evaluate();
				}
			}
		};
	}
}