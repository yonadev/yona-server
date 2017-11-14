/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.io.Serializable;

/**
 * This exception is to be used when email sending fails.
 */
public class SmsException extends YonaException
{
	private static final long serialVersionUID = 1341422955591660135L;

	private SmsException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	private SmsException(Throwable t, String messageId, Serializable... parameters)
	{
		super(t, messageId, parameters);
	}

	public static SmsException smsSendingFailed(Exception cause)
	{
		return new SmsException(cause, "error.sms.sending.failed.exception", cause.getMessage());
	}

	public static SmsException smsSendingFailed(int httpResponseCode, String httpResponseBody)
	{
		return new SmsException("error.sms.sending.failed.httpStatus", httpResponseCode, httpResponseBody);
	}
}
