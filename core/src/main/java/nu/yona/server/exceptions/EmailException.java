/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

/**
 * This exception is to be used when email sending fails.
 */
public class EmailException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private EmailException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private EmailException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static EmailException emailSendingFailed(Exception cause)
	{
		return new EmailException(cause, "error.email.sending.failed", cause.getMessage());
	}
}
