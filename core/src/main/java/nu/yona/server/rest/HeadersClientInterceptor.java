/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Intercepts client-side HTTP requests in order to add headers to the request as available in the headers holder.
 */
public class HeadersClientInterceptor implements ClientHttpRequestInterceptor
{
	private final PassThroughHeadersHolder headersHolder;

	public HeadersClientInterceptor(PassThroughHeadersHolder headersHolder)
	{
		this.headersHolder = headersHolder;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException
	{
		headersHolder.writeTo(request.getHeaders());
		return execution.execute(request, body);
	}
}