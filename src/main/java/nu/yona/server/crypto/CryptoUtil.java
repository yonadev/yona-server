package nu.yona.server.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Base64;

import nu.yona.server.exceptions.YonaException;

public class CryptoUtil {

	private CryptoUtil() {
		// No instances
	}

	public static String getRandomString(int length) {
		byte[] bytes = new byte[length * 256 / 64];
		getSecureRandomInstance().nextBytes(bytes);
		String randomString = Base64.getEncoder().encodeToString(bytes);
		return randomString.substring(0, Math.min(length, randomString.length()));
	}

	static SecureRandom getSecureRandomInstance() {
		try {
			return SecureRandom.getInstance("SHA1PRNG", "SUN");
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			throw new YonaException(e);
		}
	}
}
