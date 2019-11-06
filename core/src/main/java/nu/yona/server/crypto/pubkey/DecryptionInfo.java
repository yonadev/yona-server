/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.pubkey;

import java.util.Arrays;

import javax.crypto.SecretKey;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.SecretKeyUtil;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.Require;

public class DecryptionInfo
{

	private final SecretKey secretKey;
	private final byte[] initializationVector;

	public DecryptionInfo(SecretKey secretKey, byte[] initializationVector)
	{
		Require.that(initializationVector.length == SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH,
				() -> YonaException.illegalState("Initialization vector is " + initializationVector.length + " rather than "
						+ SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH));
		this.secretKey = secretKey;
		this.initializationVector = initializationVector;
	}

	public DecryptionInfo(byte[] bytes)
	{
		initializationVector = Arrays.copyOfRange(bytes, 0, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH);
		secretKey = CryptoUtil
				.secretKeyFromBytes(Arrays.copyOfRange(bytes, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH, bytes.length));
	}

	public SecretKey getSecretKey()
	{
		return secretKey;
	}

	public byte[] getInitializationVector()
	{
		return initializationVector;
	}

	public byte[] convertToByteArray()
	{
		byte[] secretKeyBytes = secretKey.getEncoded();
		byte[] retVal = new byte[SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH + secretKeyBytes.length];
		System.arraycopy(initializationVector, 0, retVal, 0, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH);
		System.arraycopy(secretKeyBytes, 0, retVal, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH, secretKeyBytes.length);
		return retVal;
	}
}
