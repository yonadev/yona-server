package nu.yona.server.crypto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.junit.Test;

public class CryptoSessionTest
{

	private static final int INITIALIZATION_VECTOR_LENGTH = 16;
	private static final String PASSWORD1 = "secret";
	private static final String PASSWORD2 = "easy";
	private static final String PLAINTEXT1 = "One";
	private static final String PLAINTEXT2 = "Two";

	@Test(expected = CryptoException.class)
	public void testNoCurrentSession()
	{
		CryptoSession.getCurrent();
	}

	@Test(expected = MissingPasswordException.class)
	public void testExecuteEmptyPassword()
	{
		CryptoSession.execute(Optional.empty(), () -> "Done");
	}

	@Test
	public void testStringReturnValue()
	{
		assertThat(PLAINTEXT1, equalTo(CryptoSession.execute(Optional.of(PASSWORD1), () -> PLAINTEXT1)));
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
		decrypt(PASSWORD2, ciphertext, initializationVector);
	}

	@Test
	public void testInvalidPasswordWhileCheckerSaysOK()
	{
		assertThat(PLAINTEXT1,
				equalTo(CryptoSession.execute(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsOK, () -> PLAINTEXT1)));
	}

	@Test(expected = CryptoException.class)
	public void testInvalidPasswordWhileCheckerSaysNotOK()
	{
		CryptoSession.execute(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsNotOK, () -> PLAINTEXT1);
	}

	private static boolean passwordIsOK()
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

	private static boolean passwordIsNotOK()
	{
		return false;
	}

	private static String encrypt(String password, String plaintext, byte[] initializationVector, boolean ivIsInput)
	{
		return CryptoSession.execute(Optional.of(password),
				() -> encryptInCurrentSession(plaintext, initializationVector, ivIsInput));
	}

	private static String decrypt(String password, String ciphertext, byte[] initializationVector)
	{
		return CryptoSession.execute(Optional.of(password), () -> decryptInCurrentSession(ciphertext, initializationVector));
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
}
