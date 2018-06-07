/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import nu.yona.server.Translator;
import nu.yona.server.exceptions.YonaException;

/**
 * This class contains the mapping for the different exceptions and how they should be mapped to an http response
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
	public ErrorResponseDto handleOtherException(Exception exception, HttpServletRequest request)
	{
		Optional<String> opt = Optional.empty();
		logger.debug(opt.get());
		return logUnhandledExceptionAndCreateErrorDto("completed with unknown exception", exception, request);
	}

	/**
	 * This method generically handles the Yona exceptions. They are translated into nice ResponseMessage objects so the response
	 * data is properly organized and JSON parseable.
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler(YonaException.class)
	public ResponseEntity<ErrorResponseDto> handleYonaException(YonaException exception, HttpServletRequest request)
	{
		logUnhandledException("completed with Yona exception", buildRequestInfo(request), exception);

		ErrorResponseDto responseMessage = new ErrorResponseDto(exception.getMessageId(), exception.getMessage());

		return new ResponseEntity<>(responseMessage, exception.getStatusCode());
	}

	/**
	 * The request is wrong. Examples: The caller passed a wrong parameter (e.g. an invalid UUID), the HTTP message cannot be read
	 * (e.g. because the JSON string is wrong), etc.<br/>
	 * Such requests result in a 400 (Bad Request).
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public ErrorResponseDto handleInvalidRequestException(Exception exception, HttpServletRequest request)
	{
		return logUnhandledExceptionAndCreateErrorDto("cannot be read", exception, request);
	}

	/**
	 * Unsupported media type. Such requests result in a 415 (Unsupported Media Type).
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	@ResponseBody
	public ErrorResponseDto handleUnsupportedMediaTypeException(HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request)
	{
		return logUnhandledExceptionAndCreateErrorDto("uses unsupported media type", exception, request);
	}

	/**
	 * Unsupported media type. Such requests result in a 406 (Not Acceptable).
	 * 
	 * @param exception The exception.
	 * @return The response object to return.
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	@ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
	@ResponseBody
	public ErrorResponseDto handleMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException exception,
			HttpServletRequest request)
	{
		return logUnhandledExceptionAndCreateErrorDto("does not accept our supported media types", exception, request);
	}

	private ErrorResponseDto logUnhandledExceptionAndCreateErrorDto(String message, Exception exception,
			HttpServletRequest request)
	{
		logUnhandledException(message, buildRequestInfo(request), exception);

		return new ErrorResponseDto(null, exception.getMessage());
	}

	private void logUnhandledException(String message, String requestInfo, Exception exception)
	{
		Locale currentLocale = LocaleContextHolder.getLocale();
		try
		{
			LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
			logger.error(MessageFormat.format("Request {0} {1} : {2}", requestInfo, message, exception.getMessage()), exception);
		}
		finally
		{
			LocaleContextHolder.setLocale(currentLocale);
		}
	}

	public static String buildRequestInfo(HttpServletRequest request)
	{
		String queryString = request.getQueryString();
		String url = StringUtils.isBlank(queryString) ? request.getRequestURI() : request.getRequestURI() + "?" + queryString;
		return MessageFormat.format("{0} on URL {1}", request.getMethod(), url);
	}
}
