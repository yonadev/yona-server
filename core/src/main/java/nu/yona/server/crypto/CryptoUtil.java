/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang.StringUtils;

public class CryptoUtil
{
	public static final int CRYPTO_VARIANT_NUMBER_LENGTH = 1;

	private CryptoUtil()
	{
		// No instances
	}

	public static String getRandomString(int length)
	{
		byte[] bytes = getRandomBytes(length * 256 / 64);
		String randomString = Base64.getEncoder().encodeToString(bytes).replace('+', '-').replace('/', '.');
		return randomString.substring(0, Math.min(length, randomString.length()));
	}

	public static byte[] getRandomBytes(int length)
	{
		byte[] bytes = new byte[length];
		getSecureRandomInstance().nextBytes(bytes);
		return bytes;
	}

	public static String getRandomDigits(int length)
	{
		SecureRandom random = CryptoUtil.getSecureRandomInstance();
		return StringUtils.leftPad(Integer.toString(random.nextInt((int) Math.pow(10, length))), length, '0');
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

	/**
	 * Encrypts the given plaintext bytes.
	 * 
	 * @param plaintext the bytes to be encrypted
	 * @return the encrypted bytes, with the crypto variant number prepended to it.
	 */
	public static byte[] encrypt(byte cryptoVariantNumber, Cipher cipher, byte[] plaintext)
	{
		try
		{
			byte[] ciphertext = new byte[cipher.getOutputSize(plaintext.length) + CRYPTO_VARIANT_NUMBER_LENGTH];
			setCryptoVariantNumber(cryptoVariantNumber, ciphertext);
			int bytesStored = cipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 1);
			if (bytesStored != ciphertext.length - CRYPTO_VARIANT_NUMBER_LENGTH)
			{
				ciphertext = Arrays.copyOf(ciphertext, ciphertext.length + CRYPTO_VARIANT_NUMBER_LENGTH);
			}
			return ciphertext;
		}
		catch (IllegalBlockSizeException | BadPaddingException | ShortBufferException e)
		{
			throw CryptoException.encryptingData(e);
		}
	}

	public static SecretKeySpec secretKeyFromBytes(byte[] secretKeyBytes)
	{
		return new SecretKeySpec(secretKeyBytes, "AES");
	}

	/**
	 * Decrypts the given ciphertext bytes.
	 * 
	 * @param ciphertext the bytes to be decrypted, with a leading crypto variant number
	 * @return the decrypted bytes
	 */
	public static byte[] decrypt(byte cryptoVariantNumber, Cipher cipher, byte[] ciphertext)
	{
		try
		{
			assertValidCryptoVariantNumber(cryptoVariantNumber, ciphertext);
			byte[] plaintext = new byte[cipher.getOutputSize(ciphertext.length)];
			int bytesStored = cipher.doFinal(ciphertext, CRYPTO_VARIANT_NUMBER_LENGTH,
					ciphertext.length - CRYPTO_VARIANT_NUMBER_LENGTH, plaintext, 0);
			if (bytesStored != plaintext.length)
			{
				plaintext = Arrays.copyOf(plaintext, bytesStored);
			}
			return plaintext;
		}
		catch (IllegalBlockSizeException | BadPaddingException | ShortBufferException e)
		{
			throw CryptoException.decryptingData(e);
		}
	}

	private static void setCryptoVariantNumber(byte cryptoVariantNumber, byte[] ciphertext)
	{
		ciphertext[0] = cryptoVariantNumber;
	}

	private static void assertValidCryptoVariantNumber(byte cryptoVariantNumber, byte[] ciphertext)
	{
		if (ciphertext[0] != cryptoVariantNumber)
		{
			throw CryptoException.decryptingData();
		}
	}

}
