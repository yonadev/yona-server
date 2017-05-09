/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.pubkey;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.CryptoUtil;

public final class PublicKeyUtil
{
	private static final int KEY_LENGTH_BITS = 1024;
	static final int KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8;
	private static final String KEY_ALGORITHM = "RSA";
	static final byte CURRENT_SMALL_PLAINTEXT_CRYPTO_VARIANT_NUMBER = 1;
	static final String CIPHER_TYPE = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";

	private PublicKeyUtil()
	{
	}

	public static KeyPair generateKeyPair()
	{
		try
		{
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
			SecureRandom random = CryptoUtil.getSecureRandomInstance();
			keyGen.initialize(KEY_LENGTH_BITS, random);
			return keyGen.generateKeyPair();
		}
		catch (GeneralSecurityException e)
		{
			throw CryptoException.generatingKey(e);
		}
	}

	public static byte[] privateKeyToBytes(PrivateKey privateKey)
	{
		try
		{
			KeyFactory fact = KeyFactory.getInstance(PublicKeyUtil.KEY_ALGORITHM);
			PKCS8EncodedKeySpec spec = fact.getKeySpec(privateKey, PKCS8EncodedKeySpec.class);
			return spec.getEncoded();
		}
		catch (GeneralSecurityException e)
		{
			throw CryptoException.encodingPrivateKey(e);
		}
	}

	public static PrivateKey privateKeyFromBytes(byte[] privateKeyBytes)
	{
		try
		{
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
			KeyFactory fact = KeyFactory.getInstance(PublicKeyUtil.KEY_ALGORITHM);
			return fact.generatePrivate(keySpec);
		}
		catch (GeneralSecurityException e)
		{
			throw CryptoException.decodingPrivateKey(e);
		}
	}

	public static byte[] publicKeyToBytes(PublicKey publicKey)
	{
		try
		{
			KeyFactory factory = KeyFactory.getInstance(KEY_ALGORITHM);
			X509EncodedKeySpec spec = factory.getKeySpec(publicKey, X509EncodedKeySpec.class);
			return spec.getEncoded();
		}
		catch (GeneralSecurityException e)
		{
			throw CryptoException.encodingPublicKey(e);
		}
	}

	public static PublicKey publicKeyFromBytes(byte[] publicKeyBytes)
	{
		try
		{
			X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
			KeyFactory fact = KeyFactory.getInstance(KEY_ALGORITHM);
			return fact.generatePublic(spec);
		}
		catch (GeneralSecurityException e)
		{
			throw CryptoException.decodingPublicKey(e);
		}
	}
}
