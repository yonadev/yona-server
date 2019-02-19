/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponseDto
{
	private final String message;

	private final String code;

	private final String correlationId;

	protected ErrorResponseDto(String code, String message)
	{
		this(code, message, null);
	}

	@JsonCreator
	public ErrorResponseDto(@JsonProperty("code") String code, @JsonProperty("message") String message,
			@JsonProperty("correlationId") String correlationId)
	{
		this.code = code;
		this.message = message;
		this.correlationId = correlationId;
	}

	public static ErrorResponseDto createInstance(String code, String message)
	{
		return new ErrorResponseDto(code, message, ErrorLoggingFilter.LoggingContext.getCorrelationId());
	}

	public static ErrorResponseDto createInstance(String message)
	{
		return new ErrorResponseDto(null, message, ErrorLoggingFilter.LoggingContext.getCorrelationId());
	}

	public String getCode()
	{
		return code;
	}

	public String getMessage()
	{
		return message;
	}

	public String getCorrelationId()
	{
		return correlationId;
	}
}