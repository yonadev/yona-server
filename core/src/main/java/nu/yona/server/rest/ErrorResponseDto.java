/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

/**
 * This DTO is used to communicate error messages or other type of messages to the client.
 */
public class ErrorResponseDto
{
	/** Holds the actual message */
	private String message;

	/** Holds the message code. */
	private String code;

	/**
	 * Default constructor
	 */
	public ErrorResponseDto()
	{
	}

	/**
	 * Creates a new DTO based on the given message.
	 * 
	 * @param type The type of message.
	 * @param message The actual message to display.
	 */
	public ErrorResponseDto(String code, String message)
	{
		this.code = code;
		this.message = message;
	}

	/**
	 * This method gets the message code.
	 * 
	 * @return The message code.
	 */
	public String getCode()
	{
		return code;
	}

	/**
	 * This method sets the message code.
	 * 
	 * @param code The message code.
	 */
	public void setCode(String code)
	{
		this.code = code;
	}

	/**
	 * This method gets the actual message.
	 * 
	 * @return The actual message.
	 */
	public String getMessage()
	{
		return message;
	}

	/**
	 * This method sets the actual message.
	 * 
	 * @param message The actual message.
	 */
	public void setMessage(String message)
	{
		this.message = message;
	}
}
