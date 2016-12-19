package nu.yona.server.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DecryptionInfo
{

	private final String password;
	private final byte[] initializationVector;

	public DecryptionInfo(String password, byte[] initializationVector)
	{
		assert initializationVector.length == CryptoUtil.INITIALIZATION_VECTOR_LENGTH;
		this.password = password;
		this.initializationVector = initializationVector;
	}

	public DecryptionInfo(byte[] byteArray)
	{
		initializationVector = Arrays.copyOfRange(byteArray, 0, CryptoUtil.INITIALIZATION_VECTOR_LENGTH);
		password = new String(Arrays.copyOfRange(byteArray, CryptoUtil.INITIALIZATION_VECTOR_LENGTH, byteArray.length),
				StandardCharsets.UTF_8);
	}

	public String getPassword()
	{
		return password;
	}

	public byte[] getInitializationVector()
	{
		return initializationVector;
	}

	public byte[] convertToByteArray()
	{
		byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
		byte[] retVal = new byte[CryptoUtil.INITIALIZATION_VECTOR_LENGTH + passwordBytes.length];
		System.arraycopy(initializationVector, 0, retVal, 0, CryptoUtil.INITIALIZATION_VECTOR_LENGTH);
		System.arraycopy(passwordBytes, 0, retVal, CryptoUtil.INITIALIZATION_VECTOR_LENGTH, passwordBytes.length);
		return retVal;
	}
}
