/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;

/**
 * Holds the headers that are received in an incoming HTTP request, which needs to be passed-through in outgoing HTTP requests.
 * See {@link HeadersServerInterceptor} for the place where the incoming headers are stored in the holder and
 * {@link HeadersClientInterceptor} for the place where the headers are read.
 */
public class PassThroughHeadersHolder
{
	private final Map<String, String> storedHeaders = new HashMap<>();

	public void readFrom(HttpHeaders headers)
	{
		storeIfPresent(headers, RestConstants.APP_VERSION_HEADER);
	}

	private void storeIfPresent(HttpHeaders headers, String name)
	{
		String value = headers.getFirst(name);
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

	public void removeAll(Set<String> headerNames)
	{
		storedHeaders.keySet().removeAll(headerNames);
	}
}
