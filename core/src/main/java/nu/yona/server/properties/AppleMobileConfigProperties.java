/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class AppleMobileConfigProperties
{
	private String appleMobileConfigFile;
	private String signingKeyStoreFile;
	private String signingKeyStorePassword;
	private String signingAlias;

	public String getAppleMobileConfigFile()
	{
		return appleMobileConfigFile;
	}

	public void setAppleMobileConfigFile(String appleMobileConfigFile)
	{
		this.appleMobileConfigFile = appleMobileConfigFile;
	}

	public String getSigningKeyStoreFile()
	{
		return signingKeyStoreFile;
	}

	public void setSigningKeyStoreFile(String signingKeyStoreFile)
	{
		this.signingKeyStoreFile = signingKeyStoreFile;
	}

	public String getSigningKeyStorePassword()
	{
		return signingKeyStorePassword;
	}

	public void setSigningKeyStorePassword(String signingKeyStorePassword)
	{
		this.signingKeyStorePassword = signingKeyStorePassword;
	}

	public String getSigningAlias()
	{
		return signingAlias;
	}

	public void setSigningAlias(String signingAlias)
	{
		this.signingAlias = signingAlias;
	}

}
