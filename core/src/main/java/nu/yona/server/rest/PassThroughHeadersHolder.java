/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.NamedThreadLocal;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Holds the headers that are received in an incoming HTTP request, which needs to be passed-through in outgoing HTTP requests.
 * See {@link HeadersServerInterceptor} for the place where the incoming headers are stored in the holder and
 * {@link HeadersClientInterceptor} for the place where the headers are read.
 */
@Component
public class PassThroughHeadersHolder
{
	private final ThreadLocal<Map<String, String>> threadLocal = new NamedThreadLocal<Map<String, String>>(
			"PassThroughHeadersHolder") {
		@Override
		protected Map<String, String> initialValue()
		{
			return new HashMap<>();
		}
	};

	public void readFrom(HttpHeaders headers)
	{
		storeIfPresent(headers, RestConstants.APP_VERSION_HEADER);
	}

	private void storeIfPresent(HttpHeaders headers, String name)
	{
		String value = headers.getFirst(name);
		if (value != null)
		{
			threadLocal.get().put(name, value);
		}
	}

	public void writeTo(HttpHeaders headers)
	{
		threadLocal.get().forEach(headers::add);
	}

	public void importFrom(Map<String, String> headersToImport)
	{
		threadLocal.get().putAll(headersToImport);
	}

	public Map<String, String> export()
	{
		return new HashMap<>(threadLocal.get());
	}

	public void clear()
	{
		threadLocal.get().clear();
	}
}
