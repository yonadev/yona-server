/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.exceptions.YonaException;

/**
 * This class contains the mapping for the different exceptions and how they should be mapped to an http response
 * 
 * @author pgussow
 */
@ControllerAdvice
public class GlobalExceptionMapping
{
	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionMapping.class);

	/**
	 * This method generically handles the illegal argument exceptions. They are translated into nice ResponseMessage objects so
	 * the response data is properly organized and JSON parseable.
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public ErrorResponseDTO handleOtherException(Exception exception)
	{
		logger.error("Unhandled exception: " + exception.getMessage(), exception);

		return new ErrorResponseDTO(null, exception.getMessage());
	}

	/**
	 * This method generically handles the Yona exceptions. They are translated into nice ResponseMessage objects so the response
	 * data is properly organized and JSON parseable.
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler(YonaException.class)
	public ResponseEntity<ErrorResponseDTO> handleYonaException(YonaException exception)
	{
		logger.error("Unhandled exception", exception);

		ErrorResponseDTO responseMessage = new ErrorResponseDTO(exception.getMessageId(), exception.getMessage());

		return new ResponseEntity<ErrorResponseDTO>(responseMessage, exception.getStatusCode());
	}
}
