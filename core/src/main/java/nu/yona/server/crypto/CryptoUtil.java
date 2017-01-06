package nu.yona.server.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Base64;

import org.apache.commons.lang.StringUtils;

public class CryptoUtil
{

	public static String getRandomString(int length)
	{
		byte[] bytes = getRandomBytes(length * 256 / 64);
		String randomString = Base64.getEncoder().encodeToString(bytes);
		return randomString.substring(0, Math.min(length, randomString.length()));
	}

	public static byte[] getRandomBytes(int length)
	{
		byte[] bytes = new byte[length];
		getSecureRandomInstance().nextBytes(bytes);
		return bytes;
	}

	public static String getRandomDigits(int length)
	{
		SecureRandom random = CryptoUtil.getSecureRandomInstance();
		return StringUtils.leftPad("" + random.nextInt((int) Math.pow(10, length)), length, '0');
	}

	public static SecureRandom getSecureRandomInstance()
	{
		try
		{
			return SecureRandom.getInstance("SHA1PRNG", "SUN");
		}
		catch (NoSuchAlgorithmException | NoSuchProviderException e)
		{
			throw CryptoException.gettingRandomInstance(e);
		}
	}

	public static final int CRYPTO_VARIANT_NUMBER_LENGTH = 1;

}
