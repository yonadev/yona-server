/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.Serializable;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

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

@RunWith(SpringJUnit4ClassRunner.class)
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

	@BeforeClass
	public static void setUp()
	{
		originalLocale = LocaleContextHolder.getLocale();
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@AfterClass
	public static void tearDown()
	{
		LocaleContextHolder.setLocale(originalLocale);
	}

	@Test
	public void testSuccessfulMessageTranslationWithoutInsertions()
	{
		String messageId = "error.invalid.request";
		String expectedResult = "Invalid request";
		assertExceptionTranslation(expectedResult, messageId);
	}

	@Test
	public void testFailedMessageTranslationWithoutInsertions()
	{
		String messageId = "non.existing.message.id";
		String expectedResult = messageId;
		assertExceptionTranslation(expectedResult, messageId);
	}

	@Test
	public void testSuccessfulMessageTranslationWithInsertions()
	{
		String messageId = "error.sms.sending.failed.httpStatus";
		String expectedResult = "Unexpected status code received from SMS service: first. Message: second";
		assertExceptionTranslation(expectedResult, messageId, "first", "second");
	}

	@Test
	public void testFailedMessageTranslationWithInsertions()
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
