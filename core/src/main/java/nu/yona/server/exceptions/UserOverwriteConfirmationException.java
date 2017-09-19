/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.io.Serializable;

/**
 * This exception is to be used in case user overwrite confirmation code is wrong.
 */
public class UserOverwriteConfirmationException extends ConfirmationException
{
	private static final long serialVersionUID = -3653199898915579250L;

	private UserOverwriteConfirmationException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	private UserOverwriteConfirmationException(int remainingAttempts, String messageId, Serializable... parameters)
	{
		super(remainingAttempts, messageId, parameters);
	}

	public static UserOverwriteConfirmationException confirmationCodeMismatch(String mobileNumber, String code,
			int remainingAttempts)
	{
		return new UserOverwriteConfirmationException(remainingAttempts, "error.user.overwrite.confirmation.code.mismatch",
				mobileNumber, code);
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
}
