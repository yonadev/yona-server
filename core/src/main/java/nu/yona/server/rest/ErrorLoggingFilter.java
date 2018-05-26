/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.stereotype.Component;

@Component
public class ErrorLoggingFilter implements Filter
{
	@FunctionalInterface
	public interface LogMethod
	{
		void log(String format, Object... insertions);
	}

	public static class LoggingContext implements AutoCloseable
	{

		private static final String CORRELATION_ID = "yona.correlation.id";
		private static final String TRACE_ID_HEADER = "x-b3-traceid";

		private LoggingContext()
		{
			// Nothing to do here
		}

		@Override
		public void close()
		{
			MDC.remove(CORRELATION_ID);
		}

		public static LoggingContext createInstance(HttpServletRequest request)
		{
			MDC.put(CORRELATION_ID, getCorrelationId(request));
			return new LoggingContext();
		}

		private static String getCorrelationId(HttpServletRequest request)
		{
			String traceIdHeader = request.getHeader(TRACE_ID_HEADER);
			if (traceIdHeader == null)
			{
				return UUID.randomUUID().toString();
			}
			return traceIdHeader;
		}

	}

	private static final Logger logger = LoggerFactory.getLogger(ErrorLoggingFilter.class);
	private static final Map<Series, LogMethod> seriesToLoggerMap;
	static
	{
		Map<Series, LogMethod> map = new EnumMap<>(Series.class);
		map.put(Series.INFORMATIONAL, logger::info);
		map.put(Series.REDIRECTION, logger::info);
		map.put(Series.CLIENT_ERROR, logger::warn);
		map.put(Series.SERVER_ERROR, logger::error);
		seriesToLoggerMap = Collections.unmodifiableMap(map);
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException
	{
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		try (LoggingContext loggingContext = LoggingContext.createInstance(request))
		{
			chain.doFilter(request, response);
			Series responseStatus = HttpStatus.Series.valueOf(response.getStatus());
			if (responseStatus == Series.SUCCESSFUL)
			{
				return;
			}

			logResponseStatus(request, response, responseStatus);
		}
	}

	private void logResponseStatus(HttpServletRequest request, HttpServletResponse response, Series responseStatus)
	{
		LogMethod logMethod = seriesToLoggerMap.get(responseStatus);
		if (logMethod == null)
		{
			throw new IllegalStateException("Status " + responseStatus + " is not supported");
		}
		logMethod.log("Status {} returned from {}", response.getStatus(), GlobalExceptionMapping.buildRequestInfo(request));
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