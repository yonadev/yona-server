/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

/**
 * This exception is to be used in case user overwrite confirmation code is wrong.
 */
public class UserOverwriteConfirmationException extends YonaException
{
	private static final long serialVersionUID = -3653199898915579250L;
	public static final String FAILED_ATTEMPT_MESSAGE_ID = "error.user.overwrite.confirmation.code.mismatch";
	private final int remainingAttempts;

	private UserOverwriteConfirmationException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
		this.remainingAttempts = -1;
	}

	private UserOverwriteConfirmationException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
		this.remainingAttempts = -1;
	}

	public UserOverwriteConfirmationException(int remainingAttempts, String messageId, Object... parameters)
	{
		super(messageId, parameters);
		this.remainingAttempts = remainingAttempts;
	}

	public static UserOverwriteConfirmationException confirmationCodeMismatch(String mobileNumber, String code,
			int remainingAttempts)
	{
		return new UserOverwriteConfirmationException(remainingAttempts, FAILED_ATTEMPT_MESSAGE_ID, mobileNumber, code);
	}

	public static UserOverwriteConfirmationException confirmationCodeNotSet(String mobileNumber)
	{
		return new UserOverwriteConfirmationException("error.user.overwrite.confirmation.code.not.set", mobileNumber);
	}

	public static UserOverwriteConfirmationException tooManyAttempts(String mobileNumber)
	{
		return new UserOverwriteConfirmationException("error.user.overwrite.confirmation.code.too.many.failed.attempts",
				mobileNumber);
	}

	public int getRemainingAttempts()
	{
		return remainingAttempts;
	}
}
