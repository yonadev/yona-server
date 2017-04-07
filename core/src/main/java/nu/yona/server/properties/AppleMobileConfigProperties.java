/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class AppleMobileConfigProperties
{
	private String appleMobileConfigFile;
	private boolean isSigningEnabled;
	private String signingCertificateFile;
	private String signingKeyFile;
	private String signingKeyPassword;

	public String getAppleMobileConfigFile()
	{
		return appleMobileConfigFile;
	}

	public void setAppleMobileConfigFile(String appleMobileConfigFile)
	{
		this.appleMobileConfigFile = appleMobileConfigFile;
	}

	public void setSigningEnabled(boolean isSigningEnabled)
	{
		this.isSigningEnabled = isSigningEnabled;
	}

	public boolean isSigningEnabled()
	{
		return isSigningEnabled;
	}

	public String getSigningCertificateFile()
	{
		return signingCertificateFile;
	}

	public void setSigningCertificateFile(String signingCertificateFile)
	{
		this.signingCertificateFile = signingCertificateFile;
	}

	public String getSigningKeyFile()
	{
		return signingKeyFile;
	}

	public void setSigningKeyFile(String signingKeyFile)
	{
		this.signingKeyFile = signingKeyFile;
	}

	public String getSigningKeyPassword()
	{
		return signingKeyPassword;
	}

	public void setSigningKeyPassword(String signingKeyPassword)
	{
		this.signingKeyPassword = signingKeyPassword;
	}

}
