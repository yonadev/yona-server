/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.util.HibernateHelperService;
import nu.yona.server.util.Require;

@Service
class UserAssertionService
{
	/**
	 * Holds the regex to validate a valid phone number. Start with a '+' sign followed by only numbers
	 */
	private static final Pattern REGEX_PHONE = Pattern.compile("^\\+[1-9][0-9]+$");

	/**
	 * Holds the regex to validate a valid email address. Match the pattern a@b.c
	 */
	private static final Pattern REGEX_EMAIL = Pattern.compile("^[A-Z0-9._-]+@[A-Z0-9.-]+\\.[A-Z0-9.-]+$",
			Pattern.CASE_INSENSITIVE);

	@Autowired(required = false)
	private UserRepository userRepository;

	@Autowired(required = false)
	private HibernateHelperService hibernateHelperService;

	public void assertValidatedUser(User user)
	{
		user.assertMobileNumberConfirmed();
	}

	public void assertUserEntityLockedForUpdate(User userEntity)
	{
		Require.that(hibernateHelperService.isLockedForUpdate(userEntity),
				() -> YonaException.illegalState("User entity must be locked for update"));
	}

	static void assertValidUserFields(UserDto user, UserService.UserPurpose purpose)
	{
		Require.that(StringUtils.isNotBlank(user.getOwnPrivateData().getFirstName()), InvalidDataException::blankFirstName);
		Require.that(StringUtils.isNotBlank(user.getOwnPrivateData().getLastName()), InvalidDataException::blankLastName);
		Require.that(!(purpose == UserService.UserPurpose.USER && StringUtils.isBlank(user.getOwnPrivateData().getNickname())),
				InvalidDataException::blankNickname);

		Require.that(StringUtils.isNotBlank(user.getMobileNumber()), InvalidDataException::blankMobileNumber);

		assertValidMobileNumber(user.getMobileNumber());

		if (purpose == UserService.UserPurpose.BUDDY)
		{
			Require.that(StringUtils.isNotBlank(user.getEmailAddress()), InvalidDataException::blankEmailAddress);
			assertValidEmailAddress(user.getEmailAddress());

			Require.that(user.getOwnPrivateData().getGoalsIncludingHistoryItems().orElse(Collections.emptySet()).isEmpty(),
					InvalidDataException::goalsNotSupported);
		}
		else
		{
			Require.that(StringUtils.isBlank(user.getEmailAddress()), InvalidDataException::excessEmailAddress);
		}
	}

	public static void assertValidMobileNumber(String mobileNumberStr)
	{
		try
		{
			Require.that(REGEX_PHONE.matcher(mobileNumberStr).matches(),
					() -> InvalidDataException.invalidMobileNumber(mobileNumberStr));
			PhoneNumberUtil util = PhoneNumberUtil.getInstance();
			PhoneNumber mobileNumber = util.parse(mobileNumberStr, null);
			assertNoLeadingZeros(util, mobileNumberStr, mobileNumber);
			assertIsMobile(util, mobileNumberStr, mobileNumber);
		}
		catch (NumberParseException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private static void assertNoLeadingZeros(PhoneNumberUtil util, String mobileNumberStr, PhoneNumber mobileNumber)
	{
		// Format the number in the international format. Leading zeros would disappear but spaces and hyphens might be added.
		// After removal of the spaces and hyphens, the formatted number should be identical to the original one.
		if (!mobileNumberStr.equals(util.format(mobileNumber, PhoneNumberFormat.INTERNATIONAL).replaceAll("[ -]", "")))
		{
			throw InvalidDataException.numberWithLeadingZeros(mobileNumberStr);
		}
	}

	private static void assertIsMobile(PhoneNumberUtil util, String mobileNumberStr, PhoneNumber mobileNumber)
	{
		PhoneNumberType numberType = util.getNumberType(mobileNumber);
		if ((numberType != PhoneNumberType.MOBILE) && (numberType != PhoneNumberType.FIXED_LINE_OR_MOBILE))
		{
			throw InvalidDataException.notAMobileNumber(mobileNumberStr, numberType.toString());
		}
	}

	public static void assertValidEmailAddress(String emailAddress)
	{
		Require.that(REGEX_EMAIL.matcher(emailAddress).matches(), () -> InvalidDataException.invalidEmailAddress(emailAddress));
	}

	void assertUserDoesNotExist(String mobileNumber)
	{
		User existingUser = userRepository.findByMobileNumber(mobileNumber);
		if (existingUser == null)
		{
			return;
		}

		if (existingUser.isCreatedOnBuddyRequest())
		{
			throw UserServiceException.userExistsCreatedOnBuddyRequest(mobileNumber);
		}
		throw UserServiceException.userExists(mobileNumber);
	}

}
