/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class EmailProperties
{
	private final Smtp smtp = new Smtp();
	private boolean isEnabled;
	private String senderAddress;
	private String includedMediaBaseUrl;
	private String appleAppStoreUrl;
	private String googlePlayStoreUrl;

	public static class Smtp
	{
		private String protocol;
		private String host;
		private int port;
		private boolean enableAuth;
		private boolean enableStartTls;
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

		public boolean isEnableAuth()
		{
			return enableAuth;
		}

		public void setEnableAuth(boolean enableAuth)
		{
			this.enableAuth = enableAuth;
		}

		public boolean isEnableStartTls()
		{
			return enableStartTls;
		}

		public void setEnableStartTls(boolean enableStartTls)
		{
			this.enableStartTls = enableStartTls;
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

	public String getIncludedMediaBaseUrl()
	{
		return includedMediaBaseUrl;
	}

	public void setIncludedMediaBaseUrl(String includedMediaBaseUrl)
	{
		this.includedMediaBaseUrl = includedMediaBaseUrl;
	}

	public String getAppleAppStoreUrl()
	{
		return appleAppStoreUrl;
	}

	public void setAppleAppStoreUrl(String appleAppStoreUrl)
	{
		this.appleAppStoreUrl = appleAppStoreUrl;
	}

	public String getGooglePlayStoreUrl()
	{
		return googlePlayStoreUrl;
	}

	public void setGooglePlayStoreUrl(String googlePlayStoreUrl)
	{
		this.googlePlayStoreUrl = googlePlayStoreUrl;
	}
}
