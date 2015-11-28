package nu.yona.server.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("yona")
@Configuration
public class YonaProperties
{
	private final AnalysisService analysisServiceConfig = new AnalysisService();
	private final Email email = new Email();
	private final Ldap ldapConfig = new Ldap();
	private final Sms smsConfig = new Sms();

	private int newDeviceRequestExpirationDays = 1;
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

	public static class Email
	{
		private final Smtp smtp = new Smtp();
		private boolean isEnabled;
		private String senderAddress;

		public static class Smtp
		{
			private String protocol;
			private String host;
			private int port;
			private boolean useAuth;
			private boolean useStartTls;
			private String username;
			private String password;

			public String getProtocol()
			{
				return protocol;
			}

			public void setProtocol(String protocol)
			{
				this.protocol = protocol;
			}

			public String getHost()
			{
				return host;
			}

			public void setHost(String host)
			{
				this.host = host;
			}

			public int getPort()
			{
				return port;
			}

			public void setPort(int port)
			{
				this.port = port;
			}

			public boolean isUseAuth()
			{
				return useAuth;
			}

			public void setUseAuth(boolean useAuth)
			{
				this.useAuth = useAuth;
			}

			public boolean isUseStartTls()
			{
				return useStartTls;
			}

			public void setUseStartTls(boolean useStartTls)
			{
				this.useStartTls = useStartTls;
			}

			public String getUsername()
			{
				return username;
			}

			public void setUsername(String username)
			{
				this.username = username;
			}

			public String getPassword()
			{
				return password;
			}

			public void setPassword(String password)
			{
				this.password = password;
			}
		}

		public boolean isEnabled()
		{
			return isEnabled;
		}

		public void setEnabled(boolean isEnabled)
		{
			this.isEnabled = isEnabled;
		}

		public String getSenderAddress()
		{
			return senderAddress;
		}

		public void setSenderAddress(String senderAddress)
		{
			this.senderAddress = senderAddress;
		}

		public Smtp getSmtp()
		{
			return smtp;
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

	public int getPasswordLength()
	{
		return passwordLength;
	}

	public void setPasswordLength(int passwordLength)
	{
		this.passwordLength = passwordLength;
	}

	public AnalysisService getAnalysisService()
	{
		return analysisServiceConfig;
	}

	public Email getEmail()
	{
		return email;
	}

	public Ldap getLdap()
	{
		return ldapConfig;
	}

	public Sms getSms()
	{
		return smsConfig;
	}
}
