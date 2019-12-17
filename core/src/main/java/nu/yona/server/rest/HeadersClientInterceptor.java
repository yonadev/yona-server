/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class HeadersClientInterceptor implements ClientHttpRequestInterceptor
{
	private final HeadersHolder headersHolder;

	public HeadersClientInterceptor(HeadersHolder headersHolder)
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