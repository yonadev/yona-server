package nu.yona.server.properties;

public class SecurityProperties
{
	private int newDeviceRequestExpirationDays = 1;
	private int passwordLength = 32;
	private long bruteForceBlockMinutes = 60;
	private long dosMemorySeconds = 300;
	private boolean isDOSProtectionEnabled = false;

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

	public void getDOSMemorySeconds(long dosMemorySeconds)
	{
		this.dosMemorySeconds = dosMemorySeconds;
	}

	public long getDOSMemorySeconds()
	{
		return dosMemorySeconds;
	}

	public void setDOSProtectionEnabled(boolean isDOSProtectionEnabled)
	{
		this.isDOSProtectionEnabled = isDOSProtectionEnabled;
	}

	public boolean isDOSProtectionEnabled()
	{
		return isDOSProtectionEnabled;
	}
}
