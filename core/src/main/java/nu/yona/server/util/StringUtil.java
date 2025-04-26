/*
 * Copyright (c) 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.util;

import java.util.regex.Pattern;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import nu.yona.server.exceptions.InvalidDataException;

public class StringUtil
{
	private static final Pattern PLAIN_TEXT_PATTERN = Pattern.compile("^[\\p{L}_\\-0-9., ?!()']*$");
	private static final Pattern PLAIN_TEXT_WITH_URL_PATTERN = Pattern.compile("^[\\p{L}_\\-0-9., ?!()'/:]*$");

	private StringUtil()
	{
		// No instances
	}

	public static void assertPlainTextCharacters(@Nullable CharSequence toValidate, @Nonnull String id)
	{
		assertPattern(PLAIN_TEXT_PATTERN, toValidate, id);
	}

	public static void assertPlainTextCharactersWithUrls(@Nullable CharSequence toValidate, @Nonnull String id)
	{
		assertPattern(PLAIN_TEXT_WITH_URL_PATTERN, toValidate, id);
	}

	private static void assertPattern(Pattern plainTextPattern, CharSequence toValidate, String id)
	{
		if (toValidate == null || plainTextPattern.matcher(toValidate).matches())
		{
			return;
		}
		throw InvalidDataException.invalidCharacters(id, plainTextPattern.pattern());
	}

}
