/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
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

public class CryptoSession implements AutoCloseable {
	private static final Logger LOGGER = Logger.getLogger(CryptoSession.class.getName());
	private static final byte[] SALT = "0123456789012345".getBytes();
	private static final int INITIALIZATION_VECTOR_LENGTH = 16;
	private static ThreadLocal<CryptoSession> threadLocal = new ThreadLocal<>();
	private final Cipher encryptionCipher;
	private byte[] initializationVector;
	private final SecretKey secretKey;
	private CryptoSession previousCryptoSession;

	public CryptoSession(String password, CryptoSession previousCryptoSession) {
		secretKey = getSecretKey(password.toCharArray());
		encryptionCipher = getEncryptionCipher();
		this.previousCryptoSession = previousCryptoSession;
		threadLocal.set(this);
	}

	private Cipher getEncryptionCipher() {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			return cipher;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
			LOGGER.log(SEVERE, "Unexpected exception", e);
			throw new YonaException(e);
		}
	}

	@Override
	public void close() {
		LOGGER.log(FINE, "Closing crypto session on thread " + Thread.currentThread());
		threadLocal.set(previousCryptoSession);
	}

	public static CryptoSession getCurrent() {
		CryptoSession cryptoSession = threadLocal.get();
		if (cryptoSession == null) {
			LOGGER.log(SEVERE, "No active crypto session on thread " + Thread.currentThread());
			throw new IllegalStateException("No active crypto session on thread " + Thread.currentThread());
		}
		return cryptoSession;
	}

	private SecretKey getSecretKey(char[] password) {
		try {
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			KeySpec spec = new PBEKeySpec(password, SALT, 65536, 128);
			SecretKey tmp = factory.generateSecret(spec);
			return new SecretKeySpec(tmp.getEncoded(), "AES");
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			LOGGER.log(SEVERE, "Unexpected exception", e);
			throw new YonaException(e);
		}
	}

	private byte[] getInitializationVector() {
		try {
			if (initializationVector == null) {
				AlgorithmParameters params = encryptionCipher.getParameters();
				initializationVector = params.getParameterSpec(IvParameterSpec.class).getIV();
				if (initializationVector.length != INITIALIZATION_VECTOR_LENGTH) {
					throw new YonaException(
							"Wrong assumption! Expected the initialization vector to be " + INITIALIZATION_VECTOR_LENGTH
									+ " bytes but current vector is " + initializationVector.length + " bytes");
				}
			}
			return initializationVector;
		} catch (InvalidParameterSpecException e) {
			LOGGER.log(SEVERE, "Unexpected exception", e);
			throw new YonaException(e);
		}
	}

	private Cipher getDecryptionCipher(byte[] initializationVector) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(initializationVector));
			return cipher;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException e) {
			LOGGER.log(SEVERE, "Unexpected exception", e);
			throw new YonaException(e);
		}
	}

	public byte[] encrypt(byte[] plaintext) {
		try {
			byte[] ciphertext = encryptionCipher.doFinal(plaintext);
			byte[] initializationVectorPlusCiphertext = new byte[INITIALIZATION_VECTOR_LENGTH + ciphertext.length];
			System.arraycopy(getInitializationVector(), 0, initializationVectorPlusCiphertext, 0,
					INITIALIZATION_VECTOR_LENGTH);
			System.arraycopy(ciphertext, 0, initializationVectorPlusCiphertext, INITIALIZATION_VECTOR_LENGTH,
					ciphertext.length);
			return initializationVectorPlusCiphertext;
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			LOGGER.log(SEVERE, "Unexpected exception", e);
			throw new YonaException(e);
		}
	}

	public byte[] decrypt(byte[] initializationVectorPlusCiphertext) {
		try {
			byte[] decryptionInitializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
			System.arraycopy(initializationVectorPlusCiphertext, 0, decryptionInitializationVector, 0,
					INITIALIZATION_VECTOR_LENGTH);
			return getDecryptionCipher(decryptionInitializationVector).doFinal(initializationVectorPlusCiphertext,
					INITIALIZATION_VECTOR_LENGTH,
					initializationVectorPlusCiphertext.length - INITIALIZATION_VECTOR_LENGTH);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			LOGGER.log(SEVERE, "Unexpected exception", e);
			throw new YonaException(e);
		}
	}

	public static CryptoSession start(String password) {
		LOGGER.log(FINE, "Starting crypto session on thread " + Thread.currentThread());
		return new CryptoSession(password, threadLocal.get());
	}

}
