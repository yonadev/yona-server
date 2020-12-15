/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import nu.yona.server.Translator;
import nu.yona.server.properties.YonaProperties;

@Component
/**
 * This servlet filter ensures that it looks like any request always specifies a supported locale, and it adds the
 * Content-Language header to the responses. The request handling is is done by wrapping every request with a wrapper that
 * intercepts the getLocale operation. If the locale of the request does not match a supported locale, the default locale is
 * returned. The Content-Language header simply added added to response headers before passing the request and response to the
 * next filter.
 */ public class LocalizationFilter implements Filter
{
	public static class LocalizationRequestWrapper extends HttpServletRequestWrapper
	{
		private final Locale locale;
		private YonaProperties properties;

		public LocalizationRequestWrapper(YonaProperties properties, HttpServletRequest request)
		{
			super(request);
			this.properties = properties;
			this.locale = determineLocale(request.getLocale());
		}

		@Override
		public Locale getLocale()
		{
			return locale;
		}

		private Locale determineLocale(Locale requestLocale)
		{
			if (properties.getSupportedLocales().contains(requestLocale))
			{
				return requestLocale;
			}
			return properties.getDefaultLocale();
		}
	}

	@Autowired
	private YonaProperties properties;

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException
	{
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		request = new LocalizationRequestWrapper(properties, request);
		response.setHeader(HttpHeaders.CONTENT_LANGUAGE, Translator.getStandardLocaleString(request.getLocale()));

		chain.doFilter(request, response);
	}

	@Override
	public void destroy()
	{
		// Nothing to do here
	}

	@Override
	public void init(FilterConfig config) throws ServletException
	{
		// Nothing to do here
	}
}