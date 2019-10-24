/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.pubkey;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;

import org.junit.jupiter.api.Test;

import nu.yona.server.crypto.CryptoException;

public class PublicKeyDecryptorTest
{
	private static final KeyPair keyPair = PublicKeyUtil.generateKeyPair();
	private static final String PLAINTEXT1 = "One";

	@Test
	public void decrypt_validKeyPair_returnsDecryptedData()
	{
		byte[] ciphertext = encrypt(keyPair.getPublic(), PLAINTEXT1);
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(keyPair.getPrivate());

		byte[] result = decryptor.decrypt(ciphertext);

		String plaintext = new String(result, StandardCharsets.UTF_8);
		assertThat(plaintext, equalTo(PLAINTEXT1));
	}

	@Test
	public void decrypt_null_returnsNull()
	{
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(keyPair.getPrivate());

		byte[] result = decryptor.decrypt(null);

		assertThat(result, equalTo(null));
	}

	@Test
	public void decrypt_invalidKeyPair_throws()
	{
		byte[] ciphertext = encrypt(keyPair.getPublic(), PLAINTEXT1);
		KeyPair otherKeyPair = PublicKeyUtil.generateKeyPair();
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(otherKeyPair.getPrivate());

		assertThrows(CryptoException.class, () -> decryptor.decrypt(ciphertext));
	}

	@Test
	public void decrypt_unsupportedCryptoVariantNumber_throws()
	{
		byte[] ciphertext = encrypt(keyPair.getPublic(), PLAINTEXT1);
		ciphertext[0] = 13; // Unsupported crypto variant number
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(keyPair.getPrivate());

		assertThrows(CryptoException.class, () -> decryptor.decrypt(ciphertext));
	}

	private static byte[] encrypt(PublicKey publicKey, String plaintext)
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(publicKey);
		return encryptor.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
	}
}
