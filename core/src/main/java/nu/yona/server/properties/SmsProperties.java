/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class SmsProperties
{
	private boolean isEnabled = false;
	private String defaultSenderNumber = "";
	private String alphaSenderId = "";
	private String alphaSenderSupportingCountryCallingCodes = "";
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

	public String getDefaultSenderNumber()
	{
		return defaultSenderNumber;
	}

	public void setDefaultSenderNumber(String senderNumber)
	{
		this.defaultSenderNumber = senderNumber;
	}

	public String getAlphaSenderId()
	{
		return alphaSenderId;
	}

	public void setAlphaSenderId(String alphaSenderId)
	{
		this.alphaSenderId = alphaSenderId;
	}

	public String getAlphaSenderSupportingCountryCallingCodes()
	{
		return alphaSenderSupportingCountryCallingCodes;
	}

	public void setAlphaSenderSupportingCountryCallingCodes(String alphaSenderSupportingPrefixes)
	{
		this.alphaSenderSupportingCountryCallingCodes = alphaSenderSupportingPrefixes;
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
