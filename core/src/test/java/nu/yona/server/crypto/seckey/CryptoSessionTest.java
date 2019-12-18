/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import nu.yona.server.crypto.CryptoException;

public class CryptoSessionTest
{
	private static final int INITIALIZATION_VECTOR_LENGTH = 16;
	private static final String PASSWORD1 = "secret";
	private static final String PASSWORD2 = "easy";
	private static final String PLAINTEXT1 = "One";

	private static Stream<String> getPlaintextCases()
	{
		return Stream.of(PLAINTEXT1, createVeryLongPlaintext());
	}

	private static String createVeryLongPlaintext()
	{
		char[] chars = new char[2500];
		Arrays.fill(chars, 'a');
		return String.valueOf(chars);
	}

	@Test
	public void getCurrent_noCurrentSession_throws()
	{
		assertThrows(CryptoException.class, () -> CryptoSession.getCurrent());
	}

	@Test
	public void start_emptyPassword_throws()
	{
		assertThrows(WrongPasswordException.class, () -> {
			try (CryptoSession cryptoSession = CryptoSession.start(Optional.empty(), () -> true))
			{
			}
		});
	}

	@ParameterizedTest
	@MethodSource("getPlaintextCases")
	public void encrypt_default_returnsBase64EncryptedDataWithCryptoVariantNumberAsFirstByte(String plaintext)
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];

		String ciphertext = encrypt(PASSWORD1, plaintext, initializationVector);

		assertThat(ciphertext, not(equalTo(plaintext)));
		byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
		assertThat(ciphertextBytes[0], equalTo(CryptoSession.CURRENT_CRYPTO_VARIANT_NUMBER));
	}

	@ParameterizedTest
	@MethodSource("getPlaintextCases")
	public void decrypt_validPassword_returnsDecryptedData(String plaintext)
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext = encrypt(PASSWORD1, plaintext, initializationVector);

		String result = decrypt(PASSWORD1, ciphertext, initializationVector);

		assertThat(result, equalTo(plaintext));
	}

	@Test
	public void decrypt_invalidCryptoVariantNumber_throws()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		byte[] ciphertext = Base64.getDecoder().decode(encrypt(PASSWORD1, PLAINTEXT1, initializationVector));
		ciphertext[0] = 13; // Unsupported crypto variant number

		assertThrows(CryptoException.class,
				() -> decrypt(PASSWORD1, Base64.getEncoder().encodeToString(ciphertext), initializationVector));
	}

	@Test
	public void decrypt_invalidPassword_throws()
	{
		byte[] initializationVector = new byte[INITIALIZATION_VECTOR_LENGTH];
		String ciphertext = encrypt(PASSWORD1, PLAINTEXT1, initializationVector);

		assertThrows(CryptoException.class, () -> {
			String plaintext = decrypt(PASSWORD2, ciphertext, initializationVector);

			// In rare cases, decryption with a wrong password doesn't throw but delivers rubbish.
			// In such rare cases, compare the string and explicitly throw that exception.
			assertThat(plaintext, not(equalTo(PLAINTEXT1)));
			throw CryptoException.decryptingData();
		});
	}

	@Test
	public void start_passwordCheckerSaysOk_doesNotThrow()
	{
		try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsOk))
		{
			// Session-based work normally goes here.
		}
	}

	@Test
	public void start_passwordCheckerSaysNotOk_throws()
	{
		assertThrows(CryptoException.class, () -> {
			try (CryptoSession cryptoSession = CryptoSession.start(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsNotOk))
			{
				fail("Password checker inadvertently returned OK");
			}
		});
	}

	private static boolean passwordIsOk()
	{
		return true;
	}

	private static boolean passwordIsNotOk()
	{
		return false;
	}

	private static String encrypt(String password, String plaintext, byte[] initializationVector)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password))
		{
			System.arraycopy(CryptoSession.getCurrent().generateInitializationVector(), 0, initializationVector, 0,
					initializationVector.length);

			return encryptInCurrentSession(plaintext, initializationVector);
		}
	}

	private static String decrypt(String password, String ciphertext, byte[] initializationVector)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password))
		{
			CryptoSession.getCurrent().setInitializationVector(initializationVector);

			return decryptInCurrentSession(ciphertext, initializationVector);
		}
	}

	private static String encryptInCurrentSession(String plaintext, byte[] initializationVector)
	{
		return Base64.getEncoder().encodeToString(CryptoSession.getCurrent().encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
	}

	private static String decryptInCurrentSession(String ciphertext, byte[] initializationVector)
	{
		return new String(CryptoSession.getCurrent().decrypt(Base64.getDecoder().decode(ciphertext)));
	}

	static class DataContainer
	{
		public UUID uuid;
		public byte[] ciphertext;
	}
}