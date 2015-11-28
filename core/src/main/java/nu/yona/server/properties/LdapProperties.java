package nu.yona.server.properties;

public class LdapProperties
{
	private boolean isEnabled;
	private String url = "ldap://localhost:389";
	private String baseDN = "DC=yona,DC=nu";
	private String accessUserDN = "CN=Manager," + baseDN;
	private String accessUserPassword = "Top secret";

	public boolean isEnabled()
	{
		return isEnabled;
	}

	public void setEnabled(boolean isEnabled)
	{
		this.isEnabled = isEnabled;
	}

	public String getURL()
	{
		return url;
	}

	public void setURL(String url)
	{
		this.url = url;
	}

	public String getBaseDN()
	{
		return baseDN;
	}

	public void setBaseDN(String baseDN)
	{
		this.baseDN = baseDN;
	}

	public String getAccessUserDN()
	{
		return accessUserDN;
	}

	public void setAccessUserDN(String accessUserDN)
	{
		this.accessUserDN = accessUserDN;
	}

	public String getAccessUserPassword()
	{
		return accessUserPassword;
	}

	public void setAccessUserPassword(String accessUserPassword)
	{
		this.accessUserPassword = accessUserPassword;
	}

}
