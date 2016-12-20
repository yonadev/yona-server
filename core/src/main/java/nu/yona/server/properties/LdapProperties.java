/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class LdapProperties
{
	private boolean isEnabled;
	private String url = "ldap://localhost:389";
	private String baseDn = "DC=yona,DC=nu";
	private String accessUserDn = "CN=Manager," + baseDn;
	private String accessUserPassword = "Top secret";

	public boolean isEnabled()
	{
		return isEnabled;
	}

	public void setEnabled(boolean isEnabled)
	{
		this.isEnabled = isEnabled;
	}

	public String getUrl()
	{
		return url;
	}

	public void setUrl(String url)
	{
		this.url = url;
	}

	public String getBaseDn()
	{
		return baseDn;
	}

	public void setBaseDn(String baseDn)
	{
		this.baseDn = baseDn;
	}

	public String getAccessUserDn()
	{
		return accessUserDn;
	}

	public void setAccessUserDn(String accessUserDn)
	{
		this.accessUserDn = accessUserDn;
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
