package nu.yona.server.crypto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.junit.Test;

public class CryptoSessionTest {

	private static final String PASSWORD1 = "secret";
	private static final String PASSWORD2 = "easy";
	private static final String PLAINTEXT1 = "Done";

	@Test(expected = IllegalStateException.class)
	public void testNoCurrentSession() {
		CryptoSession.getCurrent();
	}

	@Test(expected = MissingPasswordException.class)
	public void testExecuteEmptyPassword() {
		CryptoSession.execute(Optional.empty(), () -> "Done");
	}

	@Test
	public void testStringReturnValue() {
		assertThat(PLAINTEXT1, equalTo(CryptoSession.execute(Optional.of(PASSWORD1), () -> PLAINTEXT1)));
	}

	@Test
	public void testValidPassword() {
		String ciphertext = encrypt(PASSWORD1, PLAINTEXT1);
		assertThat(ciphertext, not(equalTo(PLAINTEXT1)));
		String plaintext = decrypt(PASSWORD1, ciphertext);
		assertThat(plaintext, equalTo(PLAINTEXT1));
	}

	@Test(expected = DecryptionException.class)
	public void testInvalidPassword() {
		String ciphertext = encrypt(PASSWORD1, PLAINTEXT1);
		assertThat(ciphertext, not(equalTo(PLAINTEXT1)));
		decrypt(PASSWORD2, ciphertext);
	}

	@Test
	public void testInvalidPasswordWithCheckerOK() {
		assertThat(PLAINTEXT1, equalTo(
				CryptoSession.execute(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsOK, () -> PLAINTEXT1)));
	}

	@Test(expected = DecryptionException.class)
	public void testInvalidPasswordWithCheckerNotOK() {
		CryptoSession.execute(Optional.of(PASSWORD1), CryptoSessionTest::passwordIsNotOK, () -> PLAINTEXT1);
	}

	private static boolean passwordIsOK() {
		testEncryptDecryptInCurrentSession();
		return true;
	}

	private static void testEncryptDecryptInCurrentSession() {
		String ciphertext = encryptInCurrentSession(PLAINTEXT1);
		assertThat(ciphertext, not(equalTo(PLAINTEXT1)));
		String plaintext = decryptInCurrentSession(ciphertext);
		assertThat(plaintext, equalTo(PLAINTEXT1));
	}

	private static boolean passwordIsNotOK() {
		return false;
	}

	private static String encrypt(String password, String plaintext) {
		return CryptoSession.execute(Optional.of(password), () -> encryptInCurrentSession(plaintext));
	}

	private static String decrypt(String password, String ciphertext) {
		return CryptoSession.execute(Optional.of(password), () -> decryptInCurrentSession(ciphertext));
	}

	private static String encryptInCurrentSession(String plaintext) {
		return Base64.getEncoder()
				.encodeToString(CryptoSession.getCurrent().encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
	}

	private static String decryptInCurrentSession(String ciphertext) {
		return new String(CryptoSession.getCurrent().decrypt(Base64.getDecoder().decode(ciphertext)));
	}
}
