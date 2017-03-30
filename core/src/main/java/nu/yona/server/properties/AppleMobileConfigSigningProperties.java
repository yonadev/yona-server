/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class AppleMobileConfigSigningProperties
{
	private boolean isSigningEnabled;
	private String signingCertificateFile;
	private String signingKeyFile;
	private String password;

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

	public String getPassword()
	{
		return password;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

}
