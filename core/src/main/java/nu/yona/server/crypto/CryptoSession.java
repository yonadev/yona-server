/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import static java.util.logging.Level.FINE;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import nu.yona.server.exceptions.YonaException;

public class CryptoSession implements AutoCloseable
{
	private static final String CIPHER_TYPE = "AES/CBC/PKCS5Padding";

	public interface Executable<T>
	{
		T execute();
	}

	public interface VoidPredicate
	{
		boolean test();
	}

	private static final Logger LOGGER = Logger.getLogger(CryptoSession.class.getName());
	private static final byte[] SALT = "0123456789012345".getBytes();
	private static final int INITIALIZATION_VECTOR_LENGTH = 16;
	private static ThreadLocal<CryptoSession> threadLocal = new ThreadLocal<>();
	private Cipher encryptionCipher;
	private Optional<byte[]> initializationVector = Optional.empty();
	private final SecretKey secretKey;
	private CryptoSession previousCryptoSession;
	private Cipher decryptionCipher;

	private CryptoSession(String password, CryptoSession previousCryptoSession)
	{
		secretKey = getSecretKey(password.toCharArray());
		this.previousCryptoSession = previousCryptoSession;
		threadLocal.set(this);
	}

	@Override
	public void close()
	{
		LOGGER.log(FINE, "Closing crypto session on thread " + Thread.currentThread());
		threadLocal.set(previousCryptoSession);
	}

	public static <T> T execute(Optional<String> password, Executable<T> executable)
	{
		return execute(password, null, executable);
	}

	public static <T> T execute(Optional<String> password, VoidPredicate passwordChecker, Executable<T> executable)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password)))
		{
			if (passwordChecker != null && !passwordChecker.test())
			{
				throw DecryptionException.decryptingData();
			}
			return executable.execute();
		}
	}

	private Cipher getEncryptionCipher()
	{
		try
		{
			if (encryptionCipher == null)
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
			return encryptionCipher;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw new YonaException(e, "error.getting.cipher", CIPHER_TYPE);
		}
	}

	private static CryptoSession start(String password)
	{
		LOGGER.fine("Starting crypto session on thread " + Thread.currentThread());
		return new CryptoSession(password, threadLocal.get());
	}

	public static CryptoSession getCurrent()
	{
		CryptoSession cryptoSession = threadLocal.get();
		if (cryptoSession == null)
		{
			throw new YonaException("error.no.active.crypto.session", Thread.currentThread());
		}
		return cryptoSession;
	}

	public byte[] encrypt(byte[] plaintext)
	{
		try
		{
			return getEncryptionCipher().doFinal(plaintext);
		}
		catch (IllegalBlockSizeException | BadPaddingException e)
		{
			throw new YonaException(e, "error.encrypting.data");
		}
	}

	public byte[] decrypt(byte[] ciphertext)
	{
		try
		{
			return getDecryptionCipher().doFinal(ciphertext);
		}
		catch (IllegalBlockSizeException | BadPaddingException e)
		{
			throw new YonaException(e, "error.decrypting.data");
		}
	}

	private SecretKey getSecretKey(char[] password)
	{
		try
		{
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			KeySpec spec = new PBEKeySpec(password, SALT, 65536, 128);
			SecretKey tmp = factory.generateSecret(spec);
			return new SecretKeySpec(tmp.getEncoded(), "AES");
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			throw new YonaException(e, "error.creating.secret.key");
		}
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
			throw new YonaException(e, "error.unexpected");
		}
	}

	private byte[] getInitializationVector()
	{
		if (!isInitializationVectorSet())
		{
			throw new YonaException("error.initializing.vector");
		}

		return initializationVector.get();
	}

	public void setInitializationVector(byte[] initializationVector)
	{
		if (initializationVector == null)
		{
			throw new YonaException("error.initializing.vector.null");
		}
		if (initializationVector.length != INITIALIZATION_VECTOR_LENGTH)
		{
			throw new YonaException("error.initializing.vector.wrong.size", initializationVector.length,
					INITIALIZATION_VECTOR_LENGTH);
		}
		if (isInitializationVectorSet())
		{
			if (Arrays.equals(initializationVector, getInitializationVector()))
			{
				return;
			}
			throw new YonaException("error.initializing.vector.overwrite");
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
					throw new YonaException("error.initializing.vector.not.set");
				}
				decryptionCipher = Cipher.getInstance(CIPHER_TYPE);
				decryptionCipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(getInitializationVector()));
			}
			return decryptionCipher;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw new YonaException(e, "error.getting.cipher", CIPHER_TYPE);
		}
	}

	private static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(() -> MissingPasswordException.passwordNotProvided());
	}
}
