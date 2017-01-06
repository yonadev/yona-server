package nu.yona.server.crypto.pubkey;

import java.util.Arrays;

import javax.crypto.SecretKey;

import nu.yona.server.crypto.seckey.SecretKeyUtil;

public class DecryptionInfo
{

	private final SecretKey secretKey;
	private final byte[] initializationVector;

	public DecryptionInfo(SecretKey secretKey, byte[] initializationVector)
	{
		assert initializationVector.length == SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH;
		this.secretKey = secretKey;
		this.initializationVector = initializationVector;
	}

	public DecryptionInfo(byte[] bytes)
	{
		initializationVector = Arrays.copyOfRange(bytes, 0, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH);
		secretKey = SecretKeyUtil
				.secretKeyFromBytes(Arrays.copyOfRange(bytes, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH, bytes.length));
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
		byte[] retVal = new byte[SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH + secreteKeyBytes.length];
		System.arraycopy(initializationVector, 0, retVal, 0, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH);
		System.arraycopy(secreteKeyBytes, 0, retVal, SecretKeyUtil.INITIALIZATION_VECTOR_LENGTH, secreteKeyBytes.length);
		return retVal;
	}
}
