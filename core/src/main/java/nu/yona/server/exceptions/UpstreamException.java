/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.util.Optional;

import org.springframework.http.HttpStatus;

/**
 * This exception is thrown for various issues that can occur during analysis of app or network activity.
 */
public class UpstreamException extends YonaException
{
	private static final long serialVersionUID = 3472480838017894334L;
	private final Optional<String> message;

	private UpstreamException(HttpStatus statusCode, String messageId, String message)
	{
		super(statusCode, messageId);
		this.message = Optional.of(message);
	}

	private UpstreamException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
		message = Optional.empty();
	}

	@Override
	public String getLocalizedMessage()
	{
		return message.orElse(super.getLocalizedMessage());
	}

	public static UpstreamException yonaException(HttpStatus statusCode, String messageId, String message)
	{
		return new UpstreamException(statusCode, messageId, message);
	}

	public static UpstreamException analysisEngineError(HttpStatus statusCode, String body)
	{
		return new UpstreamException("error.from.analysengine", statusCode, body);
	}
}
