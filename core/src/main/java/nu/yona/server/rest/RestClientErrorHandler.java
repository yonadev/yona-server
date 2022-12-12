/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
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
		String responseBody = convertStreamToString(response.getBody());
		Optional<ErrorResponseDto> yonaErrorResponse = getYonaErrorResponse(response, responseBody);
		yonaErrorResponse.ifPresent(yer -> {
			throw UpstreamException.yonaException(getStatusCode(response), yer.getCode(), yer.getMessage());
		});
		throw UpstreamException.remoteServiceError(getStatusCode(response), responseBody);
	}

	@Override
	public boolean hasError(ClientHttpResponse response) throws IOException
	{
		return getStatusCode(response).isError();
	}

	private HttpStatusCode getStatusCode(ClientHttpResponse response)
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

	private Optional<ErrorResponseDto> getYonaErrorResponse(ClientHttpResponse response, String responseBody)
	{
		try
		{
			if (getStatusCode(response).is4xxClientError())
			{
				return Optional.ofNullable(objectMapper.readValue(responseBody, ErrorResponseDto.class));
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
