/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.io.Serializable;

import nu.yona.server.exceptions.YonaException;

public class CryptoException extends YonaException
{
	private static final long serialVersionUID = -1379944976933747626L;

	protected CryptoException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	public CryptoException(Throwable t, String messageId, Serializable... parameters)
	{
		super(t, messageId, parameters);
	}

	public static CryptoException noActiveCryptoSession(Thread thread)
	{
		return new CryptoException("error.no.active.crypto.session", thread.toString());
	}

	public static CryptoException gettingCipher(Throwable e, String name)
	{
		return new CryptoException(e, "error.getting.cipher", name);
	}

	public static CryptoException encryptingData(Throwable e)
	{
		return new CryptoException(e, "error.encrypting.data");
	}

	public static CryptoException decryptingData(Throwable e)
	{
		return new CryptoException(e, "error.decrypting.data");
	}

	public static CryptoException decryptingData()
	{
		return new CryptoException("error.decrypting.data");
	}

	public static CryptoException creatingSecretKey(Throwable e)
	{
		return new CryptoException(e, "error.creating.secret.key");
	}

	public static CryptoException initializationVectorNotSet()
	{
		return new CryptoException("error.initialization.vector.not.set");
	}

	public static CryptoException initializationVectorWrongSize(int length, int initializationVectorLength)
	{
		return new CryptoException("error.initialization.vector.wrong.size", length, initializationVectorLength);
	}

	public static CryptoException initializationVectorParameterNull()
	{
		return new CryptoException("error.initialization.vector.parameter.null");
	}

	public static CryptoException initializationVectorOverwrite()
	{
		return new CryptoException("error.initialization.vector.overwrite");
	}

	public static CryptoException gettingRandomInstance(Throwable e)
	{
		return new CryptoException(e, "error.getting.random.instance");
	}

	public static CryptoException readingUuid(Throwable e)
	{
		return new CryptoException(e, "error.reading.uuid");
	}

	public static CryptoException writingData(Throwable e)
	{
		return new CryptoException(e, "error.writing.data");
	}

	public static CryptoException generatingKey(Throwable e)
	{
		return new CryptoException(e, "error.generating.key");
	}

	public static CryptoException encodingPrivateKey(Throwable e)
	{
		return new CryptoException(e, "error.encoding.private.key");
	}

	public static CryptoException decodingPrivateKey(Throwable e)
	{
		return new CryptoException(e, "error.decoding.private.key");
	}

	public static CryptoException encodingPublicKey(Throwable e)
	{
		return new CryptoException(e, "error.encoding.public.key");
	}

	public static CryptoException decodingPublicKey(Throwable e)
	{
		return new CryptoException(e, "error.decoding.public.key");
	}
}
