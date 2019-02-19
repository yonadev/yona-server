/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class FirebaseProperties
{
	private boolean isEnabled;
	private String databaseUrl;
	private String adminServiceAccountKeyFile;

	public boolean isEnabled()
	{
		return isEnabled;
	}

	public void setEnabled(boolean isEnabled)
	{
		this.isEnabled = isEnabled;
	}

	public String getDatabaseUrl()
	{
		return databaseUrl;
	}

	public void setDatabaseUrl(String databaseUrl)
	{
		this.databaseUrl = databaseUrl;
	}

	public String getAdminServiceAccountKeyFile()
	{
		return adminServiceAccountKeyFile;
	}

	public void setAdminServiceAccountKeyFile(String adminServiceAccountKeyFile)
	{
		this.adminServiceAccountKeyFile = adminServiceAccountKeyFile;
	}
}
