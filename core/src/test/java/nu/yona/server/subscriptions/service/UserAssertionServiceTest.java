/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nu.yona.server.Translator;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.test.util.JUnitUtil;

@ExtendWith(MockitoExtension.class)
class UserAssertionServiceTest
{
	@Mock
	private Translator translator;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		JUnitUtil.setupTranslatorMock(translator);
	}

	@ParameterizedTest
	@ValueSource(strings = { "+31612345678", "+12122111111" })
	void assertValidMobileNumber_validNumber_noException(String mobileNumberStr)
	{
		assertDoesNotThrow(() -> UserAssertionService.assertValidMobileNumber(mobileNumberStr));
	}

	@ParameterizedTest
	@MethodSource("invalidNumbersSource")
	void assertValidMobileNumber_invalidNumber_exception(String mobileNumberStr, YonaException expectedException)
	{
		YonaException exception = assertThrows(expectedException.getClass(),
				() -> UserAssertionService.assertValidMobileNumber(mobileNumberStr));
		assertEquals(exception.getMessageId(), expectedException.getMessageId());
		assertEquals(exception.getMessage(), expectedException.getMessage());
	}

	private static Stream<Arguments> invalidNumbersSource()
	{
		return Stream.of(
		// @formatter:off
			Arguments.of("+31 318 123456", InvalidDataException.invalidMobileNumber("+31 318 123456")),
			Arguments.of("0318123456", InvalidDataException.invalidMobileNumber("0318123456")),
			Arguments.of("+31318123456", InvalidDataException.notAMobileNumber("+31318123456", "FIXED_LINE")),
			Arguments.of("+310612345678", InvalidDataException.numberWithLeadingZeros("+310612345678"))
		// @formatter:on
		);
	}
}
