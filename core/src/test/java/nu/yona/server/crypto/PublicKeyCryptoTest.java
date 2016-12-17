/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

import org.junit.Test;

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

	@Test
	public void testLongPlaintext()
	{
		char[] chars = new char[2500];
		Arrays.fill(chars, 'a');
		String longPlainText = String.valueOf(chars);
		byte[] ciphertext = encrypt(keyPair.getPublic(), longPlainText);
		assertThat(ciphertext.length, greaterThan(MIN_BLOCK_LENGTH));
		assertThat(ciphertext[0], equalTo(PublicKeyUtil.CURRENT_LARGE_PLAINTEXT_CRYPTO_VARIANT_NUMBER));
		String plaintext = decrypt(keyPair.getPrivate(), ciphertext);
		assertThat(plaintext, equalTo(longPlainText));
	}

	@Test
	public void testLargestSmallPlaintext()
	{
		char[] chars = new char[86];
		Arrays.fill(chars, 'a');
		String longPlainText = String.valueOf(chars);
		byte[] ciphertext = encrypt(keyPair.getPublic(), longPlainText);
		assertThat(ciphertext.length, equalTo(MIN_BLOCK_LENGTH));
		assertThat(ciphertext[0], equalTo(PublicKeyUtil.CURRENT_SMALL_PLAINTEXT_CRYPTO_VARIANT_NUMBER));
		String plaintext = decrypt(keyPair.getPrivate(), ciphertext);
		assertThat(plaintext, equalTo(longPlainText));
	}

	@Test
	public void testSmallestLargePlaintext()
	{
		char[] chars = new char[87];
		Arrays.fill(chars, 'a');
		String longPlainText = String.valueOf(chars);
		byte[] ciphertext = encrypt(keyPair.getPublic(), longPlainText);
		assertThat(ciphertext.length, greaterThan(MIN_BLOCK_LENGTH));
		assertThat(ciphertext[0], equalTo(PublicKeyUtil.CURRENT_LARGE_PLAINTEXT_CRYPTO_VARIANT_NUMBER));
		String plaintext = decrypt(keyPair.getPrivate(), ciphertext);
		assertThat(plaintext, equalTo(longPlainText));
	}

	private static byte[] encrypt(PublicKey publicKey, String plaintext)
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(publicKey);
		return encryptor.encrypt(plaintext);
	}

	private static String decrypt(PrivateKey privateKey, byte[] ciphertext)
	{
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(privateKey);
		return decryptor.decryptString(ciphertext);
	}
}
