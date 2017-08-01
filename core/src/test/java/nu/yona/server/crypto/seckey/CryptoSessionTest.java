/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.NamedParameters;
import junitparams.Parameters;
import nu.yona.server.crypto.CryptoException;

@RunWith(JUnitParamsRunner.class)
public class CryptoSessionTest
{
	private static final int INITIALIZATION_VECTOR_LENGTH = 16;
	private static final String PASSWORD1 = "secret";
	private static final String PASSWORD2 = "easy";
	private static final String PLAINTEXT1 = "One";
	private static final String PLAINTEXT2 = "Two";

	@NamedParameters("plaintextCases")
	private static String[] getPlaintextCases()
	{
		return new String[] { PLAINTEXT1, createVeryLongPlaintext() };
	}

	private static String createVeryLongPlaintext()
	{
		char[] chars = new char[2500];
		Arrays.fill(chars, 'a');
		return String.valueOf(chars);
	}

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
	@Parameters(named = "plaintextCases")
	public void encrypt_default_returnsBase64EncryptedDataWithCryptoVariantNumberAsFirstByte(String plaintext)
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];

		String ciphertext = encrypt(PASSWORD1, plaintext, initializationVector, false);

		assertThat(ciphertext, not(equalTo(plaintext)));
		byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
		assertThat(ciphertextBytes[0], equalTo(CryptoSession.CURRENT_CRYPTO_VARIANT_NUMBER));
	}

	@Test
	@Parameters(named = "plaintextCases")
	public void decrypt_validPassword_returnsDecryptedData(String plaintext)
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext = encrypt(PASSWORD1, plaintext, initializationVector, false);

		String result = decrypt(PASSWORD1, ciphertext, initializationVector);

		assertThat(result, equalTo(plaintext));
	}

	@Test(expected = CryptoException.class)
	public void decrypt_invalidCryptoVariantNumber_throws()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		byte[] ciphertext = Base64.getDecoder().decode(encrypt(PASSWORD1, PLAINTEXT1, initializationVector, false));
		ciphertext[0] = 13; // Unsupported crypto variant number

		decrypt(PASSWORD1, Base64.getEncoder().encodeToString(ciphertext), initializationVector);
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
	public void decrypt_invalidPassword_throws()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext = encrypt(PASSWORD1, PLAINTEXT1, initializationVector, false);

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
