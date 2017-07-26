/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.Test;

import nu.yona.server.crypto.CryptoException;

public class CryptoSessionTest
{

	private static final int INITIALIZATION_VECTOR_LENGTH = 16;
	private static final String PASSWORD1 = "secret";
	private static final String PASSWORD2 = "easy";
	private static final String PLAINTEXT1 = "One";
	private static final String PLAINTEXT2 = "Two";

	@Test(expected = CryptoException.class)
	public void getCurrent_noCurrentSession_throws()
	{
		CryptoSession.getCurrent();
	}

	@Test(expected = WrongPasswordException.class)
	public void start_emptyPassword_throws()
	{
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.empty(), () -> true))
		{
		}
	}

	@Test
	public void testValidPassword()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext = encrypt(PASSWORD1, PLAINTEXT1, initializationVector, false);
		assertThat(ciphertext, not(equalTo(PLAINTEXT1)));
		String plaintext = decrypt(PASSWORD1, ciphertext, initializationVector);
		assertThat(plaintext, equalTo(PLAINTEXT1));
	}

	@Test
	public void testUuid()
	{
		UUID uuid = UUID.randomUUID();
		DataContainer dataContainer = new DataContainer();
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(PASSWORD1), () -> true))
		{
			CryptoSession.getCurrent().generateInitializationVector(); // Not used
			dataContainer.ciphertext = SecretKeyUtil.encryptUuid(uuid);
			dataContainer.uuid = SecretKeyUtil.decryptUuid(dataContainer.ciphertext);
		}
		assertThat(dataContainer.ciphertext.length, greaterThan(16));
		assertThat(uuid, equalTo(dataContainer.uuid));
	}

	@Test(expected = CryptoException.class)
	public void testCryptoVariantNumber()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		byte[] ciphertext = Base64.getDecoder().decode(encrypt(PASSWORD1, PLAINTEXT1, initializationVector, false));
		assertThat(ciphertext[0], equalTo(CryptoSession.CURRENT_CRYPTO_VARIANT_NUMBER));

		ciphertext[0] = 13; // Unsupported crypto variant number
		decrypt(PASSWORD1, Base64.getEncoder().encodeToString(ciphertext), initializationVector);
	}

	@Test
	public void testLongPlaintext()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		char[] chars = new char[2500];
		Arrays.fill(chars, 'a');
		String longPlainText = String.valueOf(chars);
		String ciphertext = encrypt(PASSWORD1, longPlainText, initializationVector, false);
		assertThat(ciphertext, not(equalTo(longPlainText)));
		String plaintext = decrypt(PASSWORD1, ciphertext, initializationVector);
		assertThat(plaintext, equalTo(longPlainText));
	}

	@Test
	public void testInitializationVectorReuse()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext1 = encrypt(PASSWORD1, PLAINTEXT1, initializationVector, false);
		assertThat(ciphertext1, not(equalTo(PLAINTEXT1)));
		String ciphertext2 = encrypt(PASSWORD1, PLAINTEXT2, initializationVector, true);
		assertThat(ciphertext2, not(equalTo(PLAINTEXT2)));

		String plaintext1 = decrypt(PASSWORD1, ciphertext1, initializationVector);
		assertThat(plaintext1, equalTo(PLAINTEXT1));
		String plaintext2 = decrypt(PASSWORD1, ciphertext2, initializationVector);
		assertThat(plaintext2, equalTo(PLAINTEXT2));
	}

	@Test(expected = CryptoException.class)
	public void testInvalidPassword()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext = encrypt(PASSWORD1, PLAINTEXT1, initializationVector, false);
		assertThat(ciphertext, not(equalTo(PLAINTEXT1)));
		String plaintext = decrypt(PASSWORD2, ciphertext, initializationVector);
		// In rare cases, decryption with a wrong password doesn't throw but delivers rubbish.
		// In such rare cases, compare the string and explicitly throw that exception.
		assertThat(plaintext, not(equalTo(PLAINTEXT1)));
		throw CryptoException.decryptingData();
	}

	@Test
	public void start_passwordCheckerSaysOk_doesNotThrow()
	{
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsOk))
		{
			assertTrue(true);
		}
	}

	@Test(expected = CryptoException.class)
	public void start_passwordCheckerSaysNotOk_throws()
	{
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsNotOk))
		{
			assertTrue(false);
		}
	}

	private static boolean passwordIsOk()
	{
		testEncryptDecryptInCurrentSession();
		return true;
	}

	private static void testEncryptDecryptInCurrentSession()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext = encryptInCurrentSession(PLAINTEXT1, initializationVector, false);
		assertThat(ciphertext, not(equalTo(PLAINTEXT1)));
		String plaintext = decryptInCurrentSession(ciphertext, null);
		assertThat(plaintext, equalTo(PLAINTEXT1));
	}

	private static boolean passwordIsNotOk()
	{
		return false;
	}

	private static String encrypt(String password, String plaintext, byte[] initializationVector, boolean ivIsInput)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password))
		{
			return encryptInCurrentSession(plaintext, initializationVector, ivIsInput);
		}
	}

	private static String decrypt(String password, String ciphertext, byte[] initializationVector)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password))
		{
			return decryptInCurrentSession(ciphertext, initializationVector);
		}
	}

	private static String encryptInCurrentSession(String plaintext, byte[] initializationVector, boolean ivIsInput)
	{
		if (ivIsInput)
		{
			CryptoSession.getCurrent().setInitializationVector(initializationVector);
		}
		else
		{
			System.arraycopy(CryptoSession.getCurrent().generateInitializationVector(), 0, initializationVector, 0,
					initializationVector.length);
		}
		return Base64.getEncoder().encodeToString(CryptoSession.getCurrent().encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
	}

	private static String decryptInCurrentSession(String ciphertext, byte[] initializationVector)
	{
		if (initializationVector != null)
		{
			CryptoSession.getCurrent().setInitializationVector(initializationVector);
		}
		return new String(CryptoSession.getCurrent().decrypt(Base64.getDecoder().decode(ciphertext)));
	}

	static class DataContainer
	{
		public UUID uuid;
		public byte[] ciphertext;
	}
}
