/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.pubkey;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import org.junit.jupiter.api.Test;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.crypto.seckey.SecretKeyUtil;

public class PublicKeyEncryptorTest
{
	private static final int MIN_BLOCK_LENGTH = CryptoUtil.CRYPTO_VARIANT_NUMBER_LENGTH + PublicKeyUtil.KEY_LENGTH_BYTES;
	private static final KeyPair keyPair = PublicKeyUtil.generateKeyPair();
	private static final String PLAINTEXT1 = "One";

	@Test
	public void encrypt_default_returnsEncryptedData()
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(keyPair.getPublic());

		byte[] ciphertext = encryptor.encrypt(PLAINTEXT1.getBytes(StandardCharsets.UTF_8));

		assertThat(ciphertext.length, equalTo(MIN_BLOCK_LENGTH));
	}

	@Test
	public void encrypt_default_firstByteIsSetToCryptoVariantNumber()
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(keyPair.getPublic());

		byte[] ciphertext = encryptor.encrypt(PLAINTEXT1.getBytes(StandardCharsets.UTF_8));

		assertThat(ciphertext[0], equalTo(PublicKeyUtil.CURRENT_SMALL_PLAINTEXT_CRYPTO_VARIANT_NUMBER));
	}

	@Test
	public void encrypt_null_returnsNull()
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(keyPair.getPublic());

		byte[] ciphertext = encryptor.encrypt(null);

		assertThat(ciphertext, equalTo(null));
	}

	@Test
	public void executeInCryptoSession_default_returnsEncryptedDecryptionInfoThatCanBeUsedWithDecryptor()
	{
		PublicKeyEncryptor encryptor = PublicKeyEncryptor.createInstance(keyPair.getPublic());
		DataContainer dataContainer = new DataContainer();

		byte[] decryptionInfo = encryptor
				.executeInCryptoSession(() -> dataContainer.ciphertext = SecretKeyUtil.encryptString(PLAINTEXT1));

		assertThat(decryptionInfo.length, equalTo(MIN_BLOCK_LENGTH));
		assertThat(dataContainer.ciphertext.length, lessThan(MIN_BLOCK_LENGTH));
		PublicKeyDecryptor decryptor = PublicKeyDecryptor.createInstance(keyPair.getPrivate());
		decryptor.executeInCryptoSession(decryptionInfo, () -> {
			dataContainer.plaintext = SecretKeyUtil.decryptString(dataContainer.ciphertext);
		});
		assertThat(dataContainer.plaintext, equalTo(PLAINTEXT1));
	}

	static class DataContainer
	{
		public String plaintext;
		public byte[] ciphertext;
	}
}
