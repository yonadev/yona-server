/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test.util;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.function.Function;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

import nu.yona.server.crypto.seckey.CryptoSession;

/**
 * This JUnit rule executes each test inside a CryptoSession with the given password.
 */
public class InCryptoSessionExtension implements BeforeEachCallback, AfterEachCallback
{
	private static final String CRYPTO_SESSION_KEY = "CryptoSession";
	private static final Namespace namespace = Namespace.create(InCryptoSessionExtension.class);

	@Override
	public void beforeEach(ExtensionContext context) throws Exception
	{
		String password = ((Function<InCryptoSession, String>) InCryptoSession::value).apply(getAnnotation(context));
		context.getStore(namespace).put(CRYPTO_SESSION_KEY, CryptoSession.start(password));
	}

	InCryptoSession getAnnotation(ExtensionContext context)
	{
		AnnotatedElement annotatedElement = context.getElement().get();
		InCryptoSession annotation = annotatedElement.getAnnotation(InCryptoSession.class);
		if (annotation != null)
		{
			return annotation;
		}
		return ((Method) annotatedElement).getDeclaringClass().getAnnotation(InCryptoSession.class);
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception
	{
		CryptoSession session = context.getStore(namespace).remove(CRYPTO_SESSION_KEY, CryptoSession.class);
		session.close();
	}
}