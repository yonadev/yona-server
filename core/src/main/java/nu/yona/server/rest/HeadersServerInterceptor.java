/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

public class HeadersServerInterceptor extends HandlerInterceptorAdapter
{
	private final HeadersHolder headersHolder;

	public HeadersServerInterceptor(HeadersHolder headersHolder)
	{
		this.headersHolder = headersHolder;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
	{
		headersHolder.readFrom(new ServletServerHttpRequest(request).getHeaders());
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex)
	{
		headersHolder.clear();
	}
}
