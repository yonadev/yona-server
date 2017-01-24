/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.pubkey;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;

import javax.crypto.Cipher;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.CryptoSession;

public class PublicKeyDecryptor implements Decryptor
{
	private final PrivateKey privateKey;

	private PublicKeyDecryptor(PrivateKey privateKey)
	{
		if (privateKey == null)
		{
			throw new IllegalArgumentException("privateKey cannot be null");
		}

		this.privateKey = privateKey;
	}

	public static PublicKeyDecryptor createInstance(PrivateKey privateKey)
	{
		return new PublicKeyDecryptor(privateKey);
	}

	byte[] decrypt(byte[] ciphertext)
	{
		try
		{
			if (ciphertext == null)
			{
				return null;
			}
			Cipher decryptCipher = Cipher.getInstance(PublicKeyUtil.CIPHER_TYPE);
			decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

			return CryptoUtil.decrypt(PublicKeyUtil.CURRENT_SMALL_PLAINTEXT_CRYPTO_VARIANT_NUMBER, decryptCipher, ciphertext);
		}
		catch (GeneralSecurityException e)
		{
			throw CryptoException.decryptingData(e);
		}
	}

	@Override
	public void executeInCryptoSession(byte[] decryptionInfoBytes, Runnable runnable)
	{
		DecryptionInfo decryptionInfo = new DecryptionInfo(decrypt(decryptionInfoBytes));
		try (CryptoSession cryptoSession = CryptoSession.start(decryptionInfo.getSecretKey()))
		{
			cryptoSession.setInitializationVector(decryptionInfo.getInitializationVector());
			runnable.run();
		}
	}
}
