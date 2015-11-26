package nu.yona.server.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("yona")
@Configuration
public class YonaProperties
{
	private int newDeviceRequestExpirationDays = 1;
	private boolean isRunningInTestMode = false;
	private int passwordLength = 32;
	private String ldapURL = "ldap://localhost:389";
	private String ldapBaseDN = "DC=yona,DC=nu";
	private String ldapAccessUserDN = "CN=Manager," + ldapBaseDN;
	private String ldapAccessUserPassword = "Top secret";

	public int getNewDeviceRequestExpirationDays()
	{
		return newDeviceRequestExpirationDays;
	}

	public void setNewDeviceRequestExpirationDays(int newDeviceRequestExpiration)
	{
		this.newDeviceRequestExpirationDays = newDeviceRequestExpiration;
	}

	public boolean getIsRunningInTestMode()
	{
		return isRunningInTestMode;
	}

	public void setIsRunningInTestMode(boolean isRunningInTestMode)
	{
		this.isRunningInTestMode = isRunningInTestMode;
	}

	public int getPasswordLength()
	{
		return passwordLength;
	}

	public void setPasswordLength(int passwordLength)
	{
		this.passwordLength = passwordLength;
	}

	public String getLdapURL()
	{
		return ldapURL;
	}

	public void setLdapURL(String ldapURL)
	{
		this.ldapURL = ldapURL;
	}

	public String getLdapBaseDN()
	{
		return ldapBaseDN;
	}

	public void setLdapBaseDN(String ldapBaseDN)
	{
		this.ldapBaseDN = ldapBaseDN;
	}

	public String getLdapAccessUserDN()
	{
		return ldapAccessUserDN;
	}

	public void setLdapAccessUserDN(String ldapAccessUserDN)
	{
		this.ldapAccessUserDN = ldapAccessUserDN;
	}

	public String getLdapAccessUserPassword()
	{
		return ldapAccessUserPassword;
	}

	public void setLdapAccessUserPassword(String ldapAccessUserPassword)
	{
		this.ldapAccessUserPassword = ldapAccessUserPassword;
	}
}
