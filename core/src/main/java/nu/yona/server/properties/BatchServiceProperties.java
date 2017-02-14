/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class BatchServiceProperties
{
	private String serviceUrl = "http://localhost:8083";

	public String getServiceUrl()
	{
		return serviceUrl;
	}

	public void setServiceUrl(String serviceUrl)
	{
		this.serviceUrl = serviceUrl;
	}
}
