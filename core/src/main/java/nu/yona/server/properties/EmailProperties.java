package nu.yona.server.properties;

public class EmailProperties
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
