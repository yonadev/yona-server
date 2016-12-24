package nu.yona.server.crypto;

import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class DecryptionInfo
{

	private final SecretKey secretKey;
	private final byte[] initializationVector;

	public DecryptionInfo(SecretKey secretKey, byte[] initializationVector)
	{
		assert initializationVector.length == CryptoUtil.INITIALIZATION_VECTOR_LENGTH;
		this.secretKey = secretKey;
		this.initializationVector = initializationVector;
	}

	public DecryptionInfo(byte[] byteArray)
	{
		initializationVector = Arrays.copyOfRange(byteArray, 0, CryptoUtil.INITIALIZATION_VECTOR_LENGTH);
		secretKey = new SecretKeySpec(Arrays.copyOfRange(byteArray, CryptoUtil.INITIALIZATION_VECTOR_LENGTH, byteArray.length),
				"AES");
	}

	public SecretKey getSecretKey()
	{
		return secretKey;
	}

	public byte[] getInitializationVector()
	{
		return initializationVector;
	}

	public byte[] convertToByteArray()
	{
		byte[] secreteKeyBytes = secretKey.getEncoded();
		byte[] retVal = new byte[CryptoUtil.INITIALIZATION_VECTOR_LENGTH + secreteKeyBytes.length];
		System.arraycopy(initializationVector, 0, retVal, 0, CryptoUtil.INITIALIZATION_VECTOR_LENGTH);
		System.arraycopy(secreteKeyBytes, 0, retVal, CryptoUtil.INITIALIZATION_VECTOR_LENGTH, secreteKeyBytes.length);
		return retVal;
	}
}
