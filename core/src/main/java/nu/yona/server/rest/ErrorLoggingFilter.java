/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.stereotype.Component;

import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.Require;

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
		private static final String TRACE_ID_HEADER = "x-b3-traceid";
		private static final String CORRELATION_ID_MDC_KEY = "yona.correlation.id";
		private static final List<String> MDC_KEYS = Arrays.asList(CORRELATION_ID_MDC_KEY, Constants.APP_OS_MDC_KEY,
				Constants.APP_VERSION_CODE_MDC_KEY, Constants.APP_VERSION_NAME_MDC_KEY);

		private LoggingContext()
		{
			// Nothing to do here
		}

		@Override
		public void close()
		{
			MDC_KEYS.forEach(MDC::remove);
		}

		public static LoggingContext createInstance(HttpServletRequest request)
		{
			MDC.put(CORRELATION_ID_MDC_KEY, getCorrelationId(request));
			Optional<String> yonaAppVersionHeader = Optional.ofNullable(request.getHeader(Constants.APP_VERSION_HEADER));
			yonaAppVersionHeader.ifPresent(LoggingContext::putAppVersionContext);
			return new LoggingContext();
		}

		public static String getCorrelationId()
		{
			return MDC.get(CORRELATION_ID_MDC_KEY);
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

		private static void putAppVersionContext(String header)
		{
			String[] parts = header.split("/");
			String operatingSystem = parts[0];
			String versionCode = parts[1];
			String versionName = parts[2];
			MDC.put(Constants.APP_OS_MDC_KEY, operatingSystem);
			MDC.put(Constants.APP_VERSION_CODE_MDC_KEY, versionCode);
			MDC.put(Constants.APP_VERSION_NAME_MDC_KEY, versionName);
		}

		private static void assertValidHeaders(HttpServletRequest request)
		{
			Optional.ofNullable(request.getHeader(Constants.APP_VERSION_HEADER))
					.ifPresent(LoggingContext::validateAppVersionHeader);
		}

		private static void validateAppVersionHeader(String header)
		{
			String[] parts = header.split("/");
			Require.that(parts.length == 3, () -> InvalidDataException.invalidAppVersionHeader(header));
			assertValidOperatingSystem(parts[0]);
			assertValidVersionCode(parts[1]);
		}

		private static void assertValidOperatingSystem(String osString)
		{
			Require.that(osString.equals("ANDROID") || osString.equals("IOS"),
					() -> InvalidDataException.invalidOperatingSystem(osString));
		}

		private static void assertValidVersionCode(String versionCodeString)
		{
			try
			{
				if (Integer.parseInt(versionCodeString) > 0)
				{
					return;
				}
			}
			catch (NumberFormatException e)
			{
				// Handled below
			}
			throw InvalidDataException.invalidVersionCode(versionCodeString);
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
		try
		{
			LoggingContext.assertValidHeaders(request);
		}
		catch (YonaException e)
		{
			response.sendError(e.getStatusCode().value(), e.getMessage());
			logger.error("Invalid header", e);
			logResponseStatus(request, response, e.getStatusCode().series());
			return;
		}

		handleRequestInContext(chain, request, response);
	}

	private void handleRequestInContext(FilterChain chain, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException
	{
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
		logMethod.log("Status {} returned from {} (request content length: {})", response.getStatus(),
				GlobalExceptionMapping.buildRequestInfo(request), request.getContentLength());
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