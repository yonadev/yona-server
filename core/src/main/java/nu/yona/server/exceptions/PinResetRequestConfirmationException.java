/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.io.Serializable;

/**
 * This exception is to be used in case user overwrite confirmation code is wrong.
 */
public class PinResetRequestConfirmationException extends ConfirmationException
{
	private static final long serialVersionUID = -7971968001802595497L;

	private PinResetRequestConfirmationException(String messageId, Serializable... parameters)
	{
		super(messageId, parameters);
	}

	private PinResetRequestConfirmationException(int remainingAttempts, String messageId, Serializable... parameters)
	{
		super(remainingAttempts, messageId, parameters);
	}

	public static PinResetRequestConfirmationException confirmationCodeMismatch(String mobileNumber, String code,
			int remainingAttempts)
	{
		return new PinResetRequestConfirmationException(remainingAttempts, "error.pin.reset.request.confirmation.code.mismatch",
				mobileNumber, code);
	}

	public static PinResetRequestConfirmationException confirmationCodeNotSet(String mobileNumber)
	{
		return new PinResetRequestConfirmationException("error.pin.reset.request.confirmation.code.not.set", mobileNumber);
	}

	public static PinResetRequestConfirmationException tooManyAttempts(String mobileNumber)
	{
		return new PinResetRequestConfirmationException("error.pin.reset.request.confirmation.code.too.many.failed.attempts",
				mobileNumber);
	}
}
