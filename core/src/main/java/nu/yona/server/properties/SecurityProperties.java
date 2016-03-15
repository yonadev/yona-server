package nu.yona.server.properties;

public class SecurityProperties
{
	private int newDeviceRequestExpirationDays = 1;
	private int passwordLength = 32;
	private long bruteForceBlockMinutes = 60;
	private long dosProtectionWindowSeconds = 300;
	private boolean isDosProtectionEnabled = false;
	private int maxCreateUserAttemptsPerTimeWindow = 1;

	/**
	 * If true, Cross Origin Resource Sharing is allowed. This is necessary for Swagger UI.
	 */
	private boolean isCorsAllowed;

	public int getNewDeviceRequestExpirationDays()
	{
		return newDeviceRequestExpirationDays;
	}

	public void setNewDeviceRequestExpirationDays(int newDeviceRequestExpiration)
	{
		this.newDeviceRequestExpirationDays = newDeviceRequestExpiration;
	}

	public int getPasswordLength()
	{
		return passwordLength;
	}

	public void setPasswordLength(int passwordLength)
	{
		this.passwordLength = passwordLength;
	}

	public void setBruteForceBlockMinutes(long bruteForceBlockMinutes)
	{
		this.bruteForceBlockMinutes = bruteForceBlockMinutes;
	}

	public long getBruteForceBlockMinutes()
	{
		return bruteForceBlockMinutes;
	}

	public void setDosProtectionWindowSeconds(long dosProtectionWindowSeconds)
	{
		this.dosProtectionWindowSeconds = dosProtectionWindowSeconds;
	}

	public long getDosProtectionWindowSeconds()
	{
		return dosProtectionWindowSeconds;
	}

	public void setDosProtectionEnabled(boolean isDosProtectionEnabled)
	{
		this.isDosProtectionEnabled = isDosProtectionEnabled;
	}

	public boolean isDosProtectionEnabled()
	{
		return isDosProtectionEnabled;
	}

	public int getMaxCreateUserAttemptsPerTimeWindow()
	{
		return maxCreateUserAttemptsPerTimeWindow;
	}

	public void setMaxCreateUserAttemptsPerTimeWindow(int maxCreateUserAttemptsPerTimeWindow)
	{
		this.maxCreateUserAttemptsPerTimeWindow = maxCreateUserAttemptsPerTimeWindow;
	}

	public void setCorsAllowed(boolean isCorsAllowed)
	{
		this.isCorsAllowed = isCorsAllowed;
	}

	public boolean isCorsAllowed()
	{
		return isCorsAllowed;
	}
}
