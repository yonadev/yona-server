/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;

/**
 * Holds the headers that are received in an incoming HTTP request, which needs to be passed-through in outgoing HTTP requests.
 */
public class PassThroughHeadersHolder
{
	private final Map<String, String> storedHeaders = new HashMap<>();

	public void readFrom(HttpHeaders headers)
	{
		storeIfPresent(headers, Constants.APP_VERSION_HEADER);
	}

	private void storeIfPresent(HttpHeaders headers, String name)
	{
		String value = headers.getFirst(Constants.APP_VERSION_HEADER);
		if (value != null)
		{
			storedHeaders.put(name, value);
		}
	}

	public void writeTo(HttpHeaders headers)
	{
		storedHeaders.forEach(headers::add);
	}

	public void importFrom(Map<String, String> headersToImport)
	{
		storedHeaders.putAll(headersToImport);
	}

	public Map<String, String> export()
	{
		return new HashMap<>(storedHeaders);
	}

	public void clear()
	{
		storedHeaders.clear();
	}
}
