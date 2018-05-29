/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.exceptions.YonaException;

public class CryptoSession implements AutoCloseable
{
	private static final String AES_128_MARKER = "AES:128:";
	static final byte CURRENT_CRYPTO_VARIANT_NUMBER = 1;
	private static final String CIPHER_TYPE = "AES/CBC/PKCS5Padding";
	private static final String SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final int SECRET_KEY_LENGTH_BITS = 128;
	/**
	 * As we are using secure random passwords, a fixed salt suffices.
	 */
	private static final byte[] SALT = "0123456789012345".getBytes();

	private static final Logger logger = LoggerFactory.getLogger(CryptoSession.class);
	private static final int ITERATIONS_FOR_MULTIUSE_KEY = 1000;
	private static ThreadLocal<CryptoSession> threadLocal = new ThreadLocal<>();
	private Cipher encryptionCipher;
	private Optional<byte[]> initializationVector = Optional.empty();
	private final SecretKey secretKey;
	private final CryptoSession previousCryptoSession;
	private Cipher decryptionCipher;

	private CryptoSession(SecretKey secretKey, CryptoSession previousCryptoSession)
	{
		this.secretKey = secretKey;
		this.previousCryptoSession = previousCryptoSession;
		threadLocal.set(this);
	}

	@Override
	public void close()
	{
		logger.debug("Closing crypto session on thread {}", Thread.currentThread());
		threadLocal.set(previousCryptoSession);
	}

	private Cipher getEncryptionCipher()
	{
		if (encryptionCipher == null)
		{
			createAndInitializeCipher();
		}
		return encryptionCipher;
	}

	private void createAndInitializeCipher()
	{
		try
		{
			encryptionCipher = Cipher.getInstance(CIPHER_TYPE);
			if (!isInitializationVectorSet())
			{
				encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey);
			}
			else
			{
				encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(initializationVector.get()));
			}
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw CryptoException.gettingCipher(e, CIPHER_TYPE);
		}
	}

	public static CryptoSession start(String password)
	{
		return start(Optional.of(password), null);
	}

	public static CryptoSession start(Optional<String> optionalPassword, VoidPredicate passwordChecker)
	{
		String password = getPassword(optionalPassword);
		CryptoSession session = start(getSecretKey(password));
		try
		{
			if (passwordChecker != null && !passwordChecker.test())
			{
				throw CryptoException.decryptingData();
			}
		}
		catch (Exception e)
		{
			session.close();
			throw e;
		}

		return session;
	}

	public static CryptoSession start(SecretKey secretKey)
	{
		logger.debug("Starting crypto session on thread {}", Thread.currentThread());
		return new CryptoSession(secretKey, threadLocal.get());
	}

	public static boolean isActive()
	{
		return threadLocal.get() != null;
	}

	public static CryptoSession getCurrent()
	{
		CryptoSession cryptoSession = threadLocal.get();
		if (cryptoSession == null)
		{
			throw CryptoException.noActiveCryptoSession(Thread.currentThread());
		}
		return cryptoSession;
	}

	public String getKeyString()
	{
		return encodeAesKey(secretKey);
	}

	/**
	 * Encrypts the given plaintext bytes.
	 * 
	 * @param plaintext the bytes to be encrypted
	 * @return the encrypted bytes, with the crypto variant number prepended to it.
	 */
	public byte[] encrypt(byte[] plaintext)
	{
		return CryptoUtil.encrypt(CURRENT_CRYPTO_VARIANT_NUMBER, getEncryptionCipher(), plaintext);
	}

	/**
	 * Decrypts the given ciphertext bytes.
	 * 
	 * @param ciphertext the bytes to be decrypted, with a leading crypto variant number
	 * @return the decrypted bytes
	 */
	public byte[] decrypt(byte[] ciphertext)
	{
		return CryptoUtil.decrypt(CURRENT_CRYPTO_VARIANT_NUMBER, getDecryptionCipher(), ciphertext);
	}

	public static SecretKey getSecretKey(String password)
	{
		return getSecretKey(password, ITERATIONS_FOR_MULTIUSE_KEY);
	}

	private static SecretKey getSecretKey(String password, int iterations)
	{
		try
		{
			if (passwordIsAesKey(password))
			{
				return decodeAesKey(password);
			}
			SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM);
			KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, iterations, SECRET_KEY_LENGTH_BITS);
			SecretKey tmp = factory.generateSecret(spec);
			return CryptoUtil.secretKeyFromBytes(tmp.getEncoded());
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			throw CryptoException.creatingSecretKey(e);
		}
	}

	private static String encodeAesKey(SecretKey key)
	{
		return AES_128_MARKER + Base64.getEncoder().encodeToString(key.getEncoded());
	}

	private static SecretKey decodeAesKey(String password)
	{
		byte[] secretKeyBytes = Base64.getDecoder().decode(password.substring(AES_128_MARKER.length()));
		if (secretKeyBytes.length != 16)
		{
			throw WrongPasswordException.wrongPasswordHeaderProvided(AES_128_MARKER, 16, secretKeyBytes.length);
		}
		return CryptoUtil.secretKeyFromBytes(secretKeyBytes);
	}

	private static boolean passwordIsAesKey(String password)
	{
		return password.startsWith(AES_128_MARKER);
	}

	public byte[] generateInitializationVector()
	{
		try
		{
			byte[] newInitializationVector = getEncryptionCipher().getParameters().getParameterSpec(IvParameterSpec.class)
					.getIV();
			setInitializationVector(newInitializationVector);
			return getInitializationVector();
		}
		catch (InvalidParameterSpecException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private byte[] getInitializationVector()
	{
		if (!isInitializationVectorSet())
		{
			throw CryptoException.initializationVectorNotSet();
		}

		return initializationVector.get();
	}

	public void setInitializationVector(byte[] initializationVector)
	{
		if (initializationVector == null)
		{
			throw CryptoException.initializationVectorParameterNull();
		}
		if (initializationVector.length != SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH)
		{
			throw CryptoException.initializationVectorWrongSize(initializationVector.length,
					SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH);
		}
		if (isInitializationVectorSet())
		{
			if (Arrays.equals(initializationVector, getInitializationVector()))
			{
				return;
			}
			throw CryptoException.initializationVectorOverwrite();
		}

		this.initializationVector = Optional.of(initializationVector);
	}

	private boolean isInitializationVectorSet()
	{
		return initializationVector.isPresent();
	}

	private Cipher getDecryptionCipher()
	{
		try
		{
			if (decryptionCipher == null)
			{
				if (!isInitializationVectorSet())
				{
					throw CryptoException.initializationVectorNotSet();
				}
				decryptionCipher = Cipher.getInstance(CIPHER_TYPE);
				decryptionCipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(getInitializationVector()));
			}
			return decryptionCipher;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw CryptoException.gettingCipher(e, CIPHER_TYPE);
		}
	}

	private static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(WrongPasswordException::passwordHeaderNotProvided);
	}

	@FunctionalInterface
	public interface Executable<T>
	{
		T execute();
	}

	@FunctionalInterface
	public interface VoidPredicate
	{
		boolean test();
	}
}