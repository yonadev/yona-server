/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang.StringUtils;

public class CryptoUtil
{
	/**
	 * As we are using secure random passwords, a static salt suffices.
	 */
	static final byte[] SALT = "0123456789012345".getBytes();
	private static final String SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final int SECRET_KEY_LENGTH_BITS = 128;
	static final int CRYPTO_VARIANT_NUMBER_LENGTH = 1;
	static final String SYMMETRICAL_CIPHER_TYPE = "AES/CBC/PKCS5Padding";
	static final int INITIALIZATION_VECTOR_LENGTH = 16;

	private CryptoUtil()
	{
		// No instances
	}

	public static String getRandomString(int length)
	{
		byte[] bytes = getRandomBytes(length * 256 / 64);
		String randomString = Base64.getEncoder().encodeToString(bytes);
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
		return StringUtils.leftPad("" + random.nextInt((int) Math.pow(10, length)), length, '0');
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

	public static byte[] decryptBytes(byte[] ciphertext)
	{
		if (ciphertext == null)
		{
			return null;
		}
		return CryptoSession.getCurrent().decrypt(ciphertext);
	}

	static SecureRandom getSecureRandomInstance()
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

	static SecretKey getSecretKey(String password)
	{
		try
		{
			SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM);
			KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, 65536, SECRET_KEY_LENGTH_BITS);
			SecretKey tmp = factory.generateSecret(spec);
			return new SecretKeySpec(tmp.getEncoded(), "AES");
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			throw CryptoException.creatingSecretKey(e);
		}
	}

	static Cipher getSymmetricalEncryptionCipher(SecretKey secretKey)
	{
		return getSymmetricalEncryptionCipher(secretKey, Optional.empty());
	}

	static Cipher getSymmetricalEncryptionCipher(SecretKey secretKey, byte[] initializationVector)
	{
		return getSymmetricalEncryptionCipher(secretKey, Optional.of(initializationVector));
	}

	private static Cipher getSymmetricalEncryptionCipher(SecretKey secretKey, Optional<byte[]> initializationVector)
	{
		try
		{
			Cipher cipher = Cipher.getInstance(CryptoUtil.SYMMETRICAL_CIPHER_TYPE);
			if (initializationVector.isPresent())
			{
				cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(initializationVector.get()));
			}
			else
			{
				// Let the provider generate an initializaiton vector
				cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			}
			return cipher;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw CryptoException.gettingCipher(e, CryptoUtil.SYMMETRICAL_CIPHER_TYPE);
		}
	}

	static Cipher getSymmetricalDecryptionCipher(SecretKey secretKey, byte[] initializationVector)
	{
		try
		{
			Cipher cipher = Cipher.getInstance(CryptoUtil.SYMMETRICAL_CIPHER_TYPE);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(initializationVector));
			return cipher;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw CryptoException.gettingCipher(e, CryptoUtil.SYMMETRICAL_CIPHER_TYPE);
		}
	}

	/**
	 * Encrypts the given plaintext bytes.
	 * 
	 * @param plaintext the bytes to be encrypted
	 * @return the encrypted bytes, with the crypto variant number prepended to it.
	 */
	static byte[] encrypt(byte cryptoVariantNumber, Cipher cipher, byte[] plaintext)
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

	/**
	 * Decrypts the given ciphertext bytes.
	 * 
	 * @param ciphertext the bytes to be decrypted, with a leading crypto variant number
	 * @return the decrypted bytes
	 */
	static byte[] decrypt(byte cryptoVariantNumber, Cipher cipher, byte[] ciphertext)
	{
		try
		{
			verifyCryptoVariantNumber(cryptoVariantNumber, ciphertext);
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

	private static void verifyCryptoVariantNumber(byte cryptoVariantNumber, byte[] ciphertext)
	{
		if (ciphertext[0] != cryptoVariantNumber)
		{
			throw CryptoException.decryptingData();
		}
	}
}
