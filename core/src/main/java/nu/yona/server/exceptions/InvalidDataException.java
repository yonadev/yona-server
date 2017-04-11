/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import java.util.UUID;

/**
 * This exception is to be used in case data is wrong in DTOs. So whenever a field has a wrong value you should throw this
 * exception.
 * 
 * @author pgussow
 */
public class InvalidDataException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;

	private InvalidDataException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private InvalidDataException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static InvalidDataException userAnonymizedIdNotFound(UUID id)
	{
		return new InvalidDataException("error.useranonymizedid.not.found", id);
	}

	public static InvalidDataException blankFirstName()
	{
		return new InvalidDataException("error.user.firstname");
	}

	public static InvalidDataException blankLastName()
	{
		return new InvalidDataException("error.user.lastname");
	}

	public static InvalidDataException blankNickname()
	{
		return new InvalidDataException("error.user.nickname");
	}

	public static InvalidDataException blankMobileNumber()
	{
		return new InvalidDataException("error.user.mobile.number");
	}

	public static InvalidDataException invalidMobileNumber(String mobileNumber)
	{
		return new InvalidDataException("error.user.mobile.number.invalid", mobileNumber);
	}

	public static InvalidDataException emptyUserId()
	{
		return new InvalidDataException("error.missing.user.id");
	}

	public static InvalidDataException emptyBuddyId()
	{
		return new InvalidDataException("error.missing.buddy.id");
	}

	public static InvalidDataException missingActivityCategoryLink()
	{
		return new InvalidDataException("error.missing.activity.category.link");
	}

	public static InvalidDataException blankEmailAddress()
	{
		return new InvalidDataException("error.user.email.address");
	}

	public static InvalidDataException invalidEmailAddress(String emailAddress)
	{
		return new InvalidDataException("error.user.email.address.invalid", emailAddress);
	}

	public static InvalidDataException excessEmailAddress()
	{
		return new InvalidDataException("error.user.email.address.not.supported");
	}

	public static InvalidDataException goalsNotSupported()
	{
		return new InvalidDataException("error.user.goals.not.supported");
	}
}
