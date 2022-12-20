/*
 * Copyright (c) 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import nu.yona.server.exceptions.InvalidDataException;

class StringUtilTest
{
	@ParameterizedTest
	@ValueSource(strings = { "", "This", "that", "This is a sentence, sort of (I'd hope).", "Mon meilleur français", "0318",
			"Right? Yes!" })
	void assertPlainTextCharacters_acceptableString_noException(String arg)
	{
		StringUtil.assertPlainTextCharacters(arg, "TestString");
	}

	@ParameterizedTest
	@ValueSource(strings = { "int i[]", "<HTML>", "String j;", "See https://yona.nu" })
	void assertPlainTextCharacters_unacceptableString_throwsInvalidDataException(String arg)
	{
		String id = "TestId";
		InvalidDataException exception = assertThrows(InvalidDataException.class,
				() -> StringUtil.assertPlainTextCharacters(arg, id));
		assertThat(exception.getMessageId(), equalTo("error.request.contains.invalid.characters"));
		assertThat(exception.getMessage(), containsString(id));
		assertThat(exception.getMessage(), not(containsString(arg)));
	}

	@Test
	void assertPlainTextCharacters_null_noException()
	{
		StringUtil.assertPlainTextCharacters(null, "TestString");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "This", "that", "This is a sentence, sort of (I'd hope).", "Mon meilleur français", "0318",
			"Right? Yes!", "See https://yona.nu:443" })
	void assertPlainTextCharactersWithUrls_acceptableString_noException(String arg)
	{
		StringUtil.assertPlainTextCharactersWithUrls(arg, "TestString");
	}

	@ParameterizedTest
	@ValueSource(strings = { "int i[]", "<HTML>", "String j;" })
	void assertPlainTextCharactersWithUrls_unacceptableString_throwsInvalidDataException(String arg)
	{
		String id = "TestId";
		InvalidDataException exception = assertThrows(InvalidDataException.class,
				() -> StringUtil.assertPlainTextCharacters(arg, id));
		assertThat(exception.getMessageId(), equalTo("error.request.contains.invalid.characters"));
		assertThat(exception.getMessage(), containsString(id));
		assertThat(exception.getMessage(), not(containsString(arg)));
	}

	@Test
	void assertPlainTextCharactersWithUrls_null_noException()
	{
		StringUtil.assertPlainTextCharactersWithUrls(null, "TestString");
	}
}
