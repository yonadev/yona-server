/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.exceptions.UpstreamException;
import nu.yona.server.exceptions.YonaException;

/**
 * This handler just logs any REST client errors that occur. When this handler is configured on a REST template, callers are
 * responsible for explicitly checking the error status. This enables them to fetch data that is included in the error response
 * (in the Yona case: the code and message).
 */
public class RestClientErrorHandler implements ResponseErrorHandler
{
	private static final Logger logger = LoggerFactory.getLogger(RestClientErrorHandler.class);
	private final ObjectMapper objectMapper;

	public RestClientErrorHandler(ObjectMapper objectMapper)
	{
		this.objectMapper = objectMapper;
	}

	@Override
	public void handleError(ClientHttpResponse response) throws IOException
	{
		logger.error("Response error: {} {}", getStatusCode(response), response.getStatusText());
		Optional<ErrorResponseDto> yonaErrorResponse = getYonaErrorResponse(response);
		yonaErrorResponse.ifPresent(yer -> {
			throw UpstreamException.yonaException(getStatusCode(response), yer.getCode(), yer.getMessage());
		});
		throw UpstreamException.remoteServiceError(getStatusCode(response), convertStreamToString(response.getBody()));
	}

	@Override
	public boolean hasError(ClientHttpResponse response) throws IOException
	{
		return RestUtil.isError(getStatusCode(response));
	}

	private HttpStatus getStatusCode(ClientHttpResponse response)
	{
		try
		{
			return response.getStatusCode();
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private Optional<ErrorResponseDto> getYonaErrorResponse(ClientHttpResponse response)
	{
		try
		{
			if (getStatusCode(response).series() == HttpStatus.Series.CLIENT_ERROR)
			{
				return Optional.ofNullable(objectMapper.readValue(response.getBody(), ErrorResponseDto.class));
			}
		}
		catch (IOException e)
		{
			// Ignore and just return empty
		}
		return Optional.empty();
	}

	private static String convertStreamToString(InputStream is)
	{
		@SuppressWarnings("resource") // It's not on us to close this stream
		java.util.Scanner s = new Scanner(is).useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
	}
}
