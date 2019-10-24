/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.Serializable;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import nu.yona.server.CoreConfiguration;
import nu.yona.server.Translator;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = "nu.yona.server", includeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.Translator", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.exceptions.ResourceBasedException.TranslatorInjector", type = FilterType.REGEX) })
class MainContext
{
	@Bean(name = "messageSource")
	public ReloadableResourceBundleMessageSource messageSource()
	{
		return new CoreConfiguration().messageSource();
	}
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { MainContext.class })
public class ResourceBasedExceptionTest
{
	private static Locale originalLocale;

	private static class TestException extends ResourceBasedException
	{
		protected TestException(String messageId, Serializable... parameters)
		{
			super(messageId, parameters);
		}

		private static final long serialVersionUID = 1L;

	}

	@BeforeAll
	public static void setUp()
	{
		originalLocale = LocaleContextHolder.getLocale();
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@AfterAll
	public static void tearDown()
	{
		LocaleContextHolder.setLocale(originalLocale);
	}

	@Test
	public void getMessageGetLocalizedMessage_unparametrized_returnsTranslatedResults()
	{
		String messageId = "error.invalid.request";
		String expectedResult = "Invalid request";

		assertExceptionTranslation(expectedResult, messageId);
	}

	@Test
	public void getMessageGetLocalizedMessage_notExistingMessageIdUnparametrized_returnsMessageId()
	{
		String messageId = "non.existing.message.id";
		String expectedResult = messageId;

		assertExceptionTranslation(expectedResult, messageId);
	}

	@Test
	public void getMessageGetLocalizedMessage_parametrized_returnsTranslatedResultsWithParametersSubstituted()
	{
		String messageId = "error.sms.sending.failed.httpStatus";
		String expectedResult = "Unexpected status code received from SMS service: first. Message: second";

		assertExceptionTranslation(expectedResult, messageId, "first", "second");
	}

	@Test
	public void getMessageGetLocalizedMessage_notExistingMessageIdParametrized_returnsMessageIdAndParameters()
	{
		String messageId = "non.existing.message.id";
		String expectedResult = messageId + "; parameters: \"first\", \"second\"";

		assertExceptionTranslation(expectedResult, messageId, "first", "second");
	}

	private void assertExceptionTranslation(String expectedResult, String messageId, Serializable... parameters)
	{
		TestException exception = new TestException(messageId, parameters);
		assertThat(exception.getMessage(), equalTo(expectedResult));
		assertThat(exception.getLocalizedMessage(), equalTo(expectedResult));
	}
}
