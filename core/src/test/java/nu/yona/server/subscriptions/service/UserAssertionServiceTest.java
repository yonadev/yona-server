/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.collect.Sets;

import nu.yona.server.Translator;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service" }, includeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserAssertionService", type = FilterType.REGEX) })
class UserAssertionServiceTestConfiguration extends UserRepositoriesConfiguration
{
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { UserAssertionServiceTestConfiguration.class })
class UserAssertionServiceTest
{
	@MockBean
	private YonaProperties yonaProperties;

	@Mock
	private Translator translator;

	@Autowired
	private UserAssertionService service;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		JUnitUtil.setupTranslatorMock(translator);
		when(yonaProperties.getSupportedCountryCodes()).thenReturn(Sets.newHashSet(31, 32, 1));
	}

	@ParameterizedTest
	@ValueSource(strings = { "+31612345678", "+12122111111" })
	void assertValidMobileNumber_validNumber_noException(String mobileNumberStr)
	{
		assertDoesNotThrow(() -> service.assertValidMobileNumber(mobileNumberStr));
	}

	@ParameterizedTest
	@MethodSource("invalidNumbersSource")
	void assertValidMobileNumber_invalidNumber_exception(String mobileNumberStr, YonaException expectedException)
	{
		YonaException exception = assertThrows(expectedException.getClass(),
				() -> service.assertValidMobileNumber(mobileNumberStr));
		assertEquals(expectedException.getMessageId(), exception.getMessageId());
		assertEquals(expectedException.getMessage(), exception.getMessage());
	}

	private static Stream<Arguments> invalidNumbersSource()
	{
		return Stream.of(
		// @formatter:off
			Arguments.of("+31 318 123456", InvalidDataException.invalidMobileNumber("+31 318 123456")),
			Arguments.of("0318123456", InvalidDataException.invalidMobileNumber("0318123456")),
			Arguments.of("+31318123456", InvalidDataException.notAMobileNumber("+31318123456", "FIXED_LINE")),
			Arguments.of("+310612345678", InvalidDataException.numberWithLeadingZeros("+310612345678")),
			Arguments.of("+491512345678", InvalidDataException.countryCodeNotSupported("+491512345678", 49))
		// @formatter:on
		);
	}
}
