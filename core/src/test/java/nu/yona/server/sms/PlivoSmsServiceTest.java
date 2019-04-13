/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.sms;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nu.yona.server.properties.SmsProperties;
import nu.yona.server.properties.YonaProperties;

@ExtendWith(MockitoExtension.class)
public class PlivoSmsServiceTest
{
	private static final String TEST_ALPHA_ID = "TestId";
	private static final String TEST_DEFAULT_NUMBER = "+12345678";
	private static final String TEST_ALPHA_SUPPORTING_COUNTRY_CALLING_CODES = "+31 +49";

	@Mock
	private YonaProperties mockYonaProperties;

	@InjectMocks
	private final PlivoSmsService smsService = new PlivoSmsService();

	@BeforeEach
	public void setUp()
	{
		SmsProperties smsProperties = new SmsProperties();
		smsProperties.setAlphaSenderId(TEST_ALPHA_ID);
		smsProperties.setDefaultSenderNumber(TEST_DEFAULT_NUMBER);
		smsProperties.setAlphaSenderSupportingCountryCallingCodes(TEST_ALPHA_SUPPORTING_COUNTRY_CALLING_CODES);
		lenient().when(mockYonaProperties.getSms()).thenReturn(smsProperties);
	}

	@ParameterizedTest
	@ValueSource(strings = { "+31000000000", "+49222222222" })
	public void determineSender_targetPhoneNumberInAlphaSenderSupportingCountries_returnsAlphaId(String targetPhoneNumber)
	{
		assertThat(smsService.determineSender(targetPhoneNumber), equalTo(TEST_ALPHA_ID));
	}

	@Test
	public void determineSender_targetPhoneNumberNotInAlphaSenderSupportingCountries_returnsDefaultNumber()
	{
		assertThat(smsService.determineSender("+32111111111"), equalTo(TEST_DEFAULT_NUMBER));
	}
}
