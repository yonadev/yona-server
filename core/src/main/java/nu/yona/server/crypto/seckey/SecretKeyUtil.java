/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import nu.yona.server.crypto.CryptoException;

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
		return encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8));
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

	public static byte[] encryptDateTime(LocalDateTime plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}
		return encryptBytes(plaintext.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).getBytes(StandardCharsets.UTF_8));
	}

	public static byte[] encryptDate(LocalDate plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}
		return encryptBytes(plaintext.format(DateTimeFormatter.ISO_LOCAL_DATE).getBytes(StandardCharsets.UTF_8));
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

	public static LocalDateTime decryptDateTime(byte[] ciphertext)
	{
		if (ciphertext == null)
		{
			return null;
		}
		return LocalDateTime.parse(new String(decryptBytes(ciphertext), StandardCharsets.UTF_8),
				DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	public static LocalDate decryptDate(byte[] ciphertext)
	{
		if (ciphertext == null)
		{
			return null;
		}
		return LocalDate.parse(new String(decryptBytes(ciphertext), StandardCharsets.UTF_8), DateTimeFormatter.ISO_LOCAL_DATE);
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
