/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.CryptoUtil;

public class SecretKeyUtil
{
	public static final int INITIALIZATION_VECTOR_LENGTH = 16;

	private SecretKeyUtil()
	{
		// No instances
	}

	public static byte[] encryptUuid(UUID plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}
		ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
		bb.putLong(plaintext.getMostSignificantBits());
		bb.putLong(plaintext.getLeastSignificantBits());
		return encryptBytes(bb.array());
	}

	public static byte[] encryptString(String plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}
		return encryptBytes(plaintext.toString().getBytes(StandardCharsets.UTF_8));
	}

	public static byte[] encryptLong(Long plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}
		ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
		bb.putLong(plaintext);
		return encryptBytes(bb.array());
	}

	public static byte[] encryptBytes(byte[] plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}
		return CryptoSession.getCurrent().encrypt(plaintext);
	}

	public static UUID decryptUuid(byte[] ciphertext)
	{
		if (ciphertext == null)
		{
			return null;
		}
		ByteBuffer bb = ByteBuffer.wrap(decryptBytes(ciphertext));
		long firstLong = bb.getLong();
		long secondLong = bb.getLong();
		return new UUID(firstLong, secondLong);
	}

	public static String decryptString(byte[] ciphertext)
	{
		if (ciphertext == null)
		{
			return null;
		}
		return new String(decryptBytes(ciphertext), StandardCharsets.UTF_8);
	}

	public static Long decryptLong(byte[] ciphertext)
	{
		if (ciphertext == null)
		{
			return null;
		}
		ByteBuffer bb = ByteBuffer.wrap(decryptBytes(ciphertext));
		return bb.getLong();
	}

	public static byte[] decryptBytes(byte[] ciphertext)
	{
		if (ciphertext == null)
		{
			return null;
		}
		return CryptoSession.getCurrent().decrypt(ciphertext);
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
			byte[] ciphertext = new byte[cipher.getOutputSize(plaintext.length) + CryptoUtil.CRYPTO_VARIANT_NUMBER_LENGTH];
			setCryptoVariantNumber(cryptoVariantNumber, ciphertext);
			int bytesStored = cipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 1);
			if (bytesStored != ciphertext.length - CryptoUtil.CRYPTO_VARIANT_NUMBER_LENGTH)
			{
				ciphertext = Arrays.copyOf(ciphertext, ciphertext.length + CryptoUtil.CRYPTO_VARIANT_NUMBER_LENGTH);
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
			verifyCryptoVariantNumber(cryptoVariantNumber, ciphertext);
			byte[] plaintext = new byte[cipher.getOutputSize(ciphertext.length)];
			int bytesStored = cipher.doFinal(ciphertext, CryptoUtil.CRYPTO_VARIANT_NUMBER_LENGTH,
					ciphertext.length - CryptoUtil.CRYPTO_VARIANT_NUMBER_LENGTH, plaintext, 0);
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

	private static void verifyCryptoVariantNumber(byte cryptoVariantNumber, byte[] ciphertext)
	{
		if (ciphertext[0] != cryptoVariantNumber)
		{
			throw CryptoException.decryptingData();
		}
	}

	public static SecretKey generateRandomSecretKey()
	{
		try
		{
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(128);
			return keyGen.generateKey();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw CryptoException.generatingKey(e);
		}
	}
}
