/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.pubkey;

import java.security.GeneralSecurityException;
import java.security.PublicKey;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.crypto.seckey.SecretKeyUtil;

public class PublicKeyEncryptor implements Encryptor
{
	private final PublicKey publicKey;

	private PublicKeyEncryptor(PublicKey publicKey)
	{
		if (publicKey == null)
		{
			throw new IllegalArgumentException("publicKey cannot be null");
		}

		this.publicKey = publicKey;
	}

	public static PublicKeyEncryptor createInstance(PublicKey publicKey)
	{
		return new PublicKeyEncryptor(publicKey);
	}

	byte[] encrypt(byte[] plaintext)
	{
		try
		{
			if (plaintext == null)
			{
				return null;
			}
			Cipher encryptCipher = Cipher.getInstance(PublicKeyUtil.CIPHER_TYPE);
			encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

			return CryptoUtil.encrypt(PublicKeyUtil.CURRENT_SMALL_PLAINTEXT_CRYPTO_VARIANT_NUMBER, encryptCipher, plaintext);
		}
		catch (GeneralSecurityException e)
		{
			throw CryptoException.encryptingData(e);
		}
	}

	@Override
	public byte[] executeInCryptoSession(Runnable runnable)
	{
		SecretKey secretKey = SecretKeyUtil.generateRandomSecretKey();
		try (CryptoSession cryptoSession = CryptoSession.start(secretKey))
		{
			byte[] decryptionInfo = getDecryptionInfo(secretKey);
			runnable.run();
			return decryptionInfo;
		}
	}

	private byte[] getDecryptionInfo(SecretKey secretKey)
	{
		DecryptionInfo decryptionInfo = new DecryptionInfo(secretKey, CryptoSession.getCurrent().generateInitializationVector());
		return encrypt(decryptionInfo.convertToByteArray());
	}
}
