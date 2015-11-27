package nu.yona.server.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("yona")
@Configuration
public class YonaProperties
{
	private final AnalysisService analysisServiceConfig = new AnalysisService();
	private final Ldap ldapConfig = new Ldap();
	private final Sms smsConfig = new Sms();

	private int newDeviceRequestExpirationDays = 1;
	private boolean isRunningInTestMode = false;
	private int passwordLength = 32;

	public static class AnalysisService
	{
		private long conflictInterval = 300000L;
		private long updateSkipWindow = 5000L;

		public long getConflictInterval()
		{
			return conflictInterval;
		}

		public void setConflictInterval(long conflictInterval)
		{
			this.conflictInterval = conflictInterval;
		}

		public long getUpdateSkipWindow()
		{
			return updateSkipWindow;
		}

		public void setUpdateSkipWindow(long updateSkipWindow)
		{
			this.updateSkipWindow = updateSkipWindow;
		}
	}

	public static class Ldap
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

	public static class Sms
	{
		private boolean isEnabled = false;
		private String mobileNumberConfirmationMessage = "Yona confirmation code: {0}";
		private int mobileNumberConfirmationCodeDigits = 5;
		private String senderNumber = "";
		private String plivoUrl = "https://api.plivo.com/v1/Account/{0}/Message/";
		private String plivoAuthId = "";
		private String plivoAuthToken = "";

		public boolean isEnabled()
		{
			return isEnabled;
		}

		public void setEnabled(boolean isEnabled)
		{
			this.isEnabled = isEnabled;
		}

		public String getMobileNumberConfirmationMessage()
		{
			return mobileNumberConfirmationMessage;
		}

		public void setMobileNumberConfirmationMessage(String mobileNumberConfirmationMessage)
		{
			this.mobileNumberConfirmationMessage = mobileNumberConfirmationMessage;
		}

		public int getMobileNumberConfirmationCodeDigits()
		{
			return mobileNumberConfirmationCodeDigits;
		}

		public void setMobileNumberConfirmationCodeDigits(int mobileNumberConfirmationCodeDigits)
		{
			this.mobileNumberConfirmationCodeDigits = mobileNumberConfirmationCodeDigits;
		}

		public String getSenderNumber()
		{
			return senderNumber;
		}

		public void setSenderNumber(String senderNumber)
		{
			this.senderNumber = senderNumber;
		}

		public String getPlivoUrl()
		{
			return plivoUrl;
		}

		public void setPlivoUrl(String plivoUrl)
		{
			this.plivoUrl = plivoUrl;
		}

		public String getPlivoAuthId()
		{
			return plivoAuthId;
		}

		public void setPlivoAuthId(String plivoAuthId)
		{
			this.plivoAuthId = plivoAuthId;
		}

		public String getPlivoAuthToken()
		{
			return plivoAuthToken;
		}

		public void setPlivoAuthToken(String plivoAuthToken)
		{
			this.plivoAuthToken = plivoAuthToken;
		}
	}

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

	public Ldap getLdap()
	{
		return ldapConfig;
	}

	public AnalysisService getAnalysisService()
	{
		return analysisServiceConfig;
	}

	public Sms getSms()
	{
		return smsConfig;
	}
}
