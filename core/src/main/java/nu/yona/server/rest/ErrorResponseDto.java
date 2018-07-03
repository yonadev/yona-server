/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This error response is used to return the error details to the client.
 */
public class ErrorResponseDto
{
	/** The textual error message */
	private final String message;

	/** The error code. */
	private final String code;

	/** ID that correlates the server log messages to this error */
	private final String correlationId;

	/**
	 * Creates a new error response based on the given message and code.
	 * 
	 * @param code The error code.
	 * @param message The textual error message.
	 */
	protected ErrorResponseDto(String code, String message)
	{
		this(code, message, null);
	}

	/**
	 * Creates a new error response based on the given message, code and correlation ID.
	 * 
	 * @param code The error code.
	 * @param message The textual error message.
	 * @param correlationId The ID that correlates the server log messages to this error.
	 */
	@JsonCreator
	public ErrorResponseDto(@JsonProperty("code") String code, @JsonProperty("message") String message,
			@JsonProperty("correlationId") String correlationId)
	{
		this.code = code;
		this.message = message;
		this.correlationId = correlationId;
	}

	/**
	 * Creates a new error response based on the given message and code.
	 * 
	 * @param code The error code.
	 * @param message The textual error message.
	 */
	public static ErrorResponseDto createInstance(String code, String message)
	{
		return new ErrorResponseDto(code, message, ErrorLoggingFilter.LoggingContext.getCorrelationId());
	}

	/**
	 * Creates a new error response based on the given message.
	 * 
	 * @param message The textual error message.
	 */
	public static ErrorResponseDto createInstance(String message)
	{
		return new ErrorResponseDto(null, message, ErrorLoggingFilter.LoggingContext.getCorrelationId());
	}

	/**
	 * Returns the error code.
	 * 
	 * @return The error code.
	 */
	public String getCode()
	{
		return code;
	}

	/**
	 * Returns the textual error message.
	 * 
	 * @return The textual error message.
	 */
	public String getMessage()
	{
		return message;
	}

	/**
	 * Returns the ID that correlates the server log messages to this error.
	 * 
	 * @return The ID that correlates the server log messages to this error.
	 */
	public String getCorrelationId()
	{
		return correlationId;
	}
}