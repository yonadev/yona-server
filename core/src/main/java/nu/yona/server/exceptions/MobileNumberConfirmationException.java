/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

/**
 * This exception is to be used in case the mobile number confirmation code is wrong.
 */
public class MobileNumberConfirmationException extends ConfirmationException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private MobileNumberConfirmationException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private MobileNumberConfirmationException(int remainingAttempts, String messageId, Object... parameters)
	{
		super(remainingAttempts, messageId, parameters);
	}

	public static MobileNumberConfirmationException confirmationCodeMismatch(String mobileNumber, String code,
			int remainingAttempts)
	{
		return new MobileNumberConfirmationException(remainingAttempts, "error.mobile.number.confirmation.code.mismatch",
				mobileNumber, code);
	}

	public static MobileNumberConfirmationException confirmationCodeNotSet(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.confirmation.code.not.set", mobileNumber);
	}

	public static MobileNumberConfirmationException mobileNumberAlreadyConfirmed(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.already.confirmed", mobileNumber);
	}

	public static MobileNumberConfirmationException notConfirmed(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.not.confirmed", mobileNumber);
	}

	public static MobileNumberConfirmationException tooManyAttempts(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.confirmation.code.too.many.failed.attempts",
				mobileNumber);
	}
}
