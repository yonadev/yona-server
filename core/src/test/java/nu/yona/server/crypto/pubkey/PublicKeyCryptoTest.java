/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.pubkey;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.junit.Test;

import nu.yona.server.crypto.CryptoException;
import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.SecretKeyUtil;

public class PublicKeyCryptoTest
{
	private static final int MIN_BLOCK_LENGTH = CryptoUtil.CRYPTO_VARIANT_NUMBER_LENGTH + PublicKeyUtil.KEY_LENGTH_BYTES;
	private static final KeyPair keyPair = PublicKeyUtil.generateKeyPair();
	private static final String PLAINTEXT1 = "One";

	@Test
	public void testValidKeyPair()
	{
		byte[] ciphertext = encrypt(keyPair.getPublic(), PLAINTEXT1);
		assertThat(ciphertext.length, equalTo(MIN_BLOCK_LENGTH));
		String plaintext = decrypt(keyPair.getPrivate(), ciphertext);
		assertThat(plaintext, equalTo(PLAINTEXT1));
	}

	@Test
	public void testDecryptionInfo()
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(keyPair.getPublic());
		DataContainer dataContainer = new DataContainer();
		dataContainer.decryptionInfo = encryptor
				.executeInCryptoSession(() -> dataContainer.ciphertext = SecretKeyUtil.encryptString(PLAINTEXT1));

		assertThat(dataContainer.decryptionInfo.length, equalTo(MIN_BLOCK_LENGTH));
		assertThat(dataContainer.ciphertext.length, lessThan(MIN_BLOCK_LENGTH));

		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(keyPair.getPrivate());
		decryptor.executeInCryptoSession(dataContainer.decryptionInfo, () -> {
			dataContainer.plaintext = SecretKeyUtil.decryptString(dataContainer.ciphertext);
		});
		assertThat(dataContainer.plaintext, equalTo(PLAINTEXT1));
	}

	@Test(expected = CryptoException.class)
	public void testInvalidKeyPair()
	{
		byte[] ciphertext = encrypt(keyPair.getPublic(), PLAINTEXT1);
		assertThat(ciphertext.length, equalTo(MIN_BLOCK_LENGTH));
		KeyPair otherKeyPair = PublicKeyUtil.generateKeyPair();
		decrypt(otherKeyPair.getPrivate(), ciphertext);
	}

	@Test(expected = CryptoException.class)
	public void testCryptoVariantNumber()
	{
		byte[] ciphertext = encrypt(keyPair.getPublic(), PLAINTEXT1);
		assertThat(ciphertext[0], equalTo(PublicKeyUtil.CURRENT_SMALL_PLAINTEXT_CRYPTO_VARIANT_NUMBER));

		ciphertext[0] = 13; // Unsupported crypto variant number
		decrypt(keyPair.getPrivate(), ciphertext);
	}

	private static byte[] encrypt(PublicKey publicKey, String plaintext)
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(publicKey);
		return encryptor.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
	}

	private static String decrypt(PrivateKey privateKey, byte[] ciphertext)
	{
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(privateKey);
		return new String(decryptor.decrypt(ciphertext), StandardCharsets.UTF_8);
	}

	static class DataContainer
	{
		public String plaintext;
		public byte[] decryptionInfo;
		public byte[] ciphertext;
	}
}
