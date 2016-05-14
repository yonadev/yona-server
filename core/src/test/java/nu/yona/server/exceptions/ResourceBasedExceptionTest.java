/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@Configuration
@ComponentScan(basePackages = { "nu.yona.server" })
@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
class MainContext
{
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { MainContext.class })
public class ResourceBasedExceptionTest
{

	private static class TestException extends ResourceBasedException
	{
		protected TestException(String messageId, Object... parameters)
		{
			super(messageId, parameters);
		}

		private static final long serialVersionUID = 1L;

	}

	// @Test
	public void testSuccessfulMessageTranslationWithoutInsertions()
	{
		String messageId = "error.invalid.request";
		String expectedResult = "Invalid request";
		assertExceptionTranslation(expectedResult, messageId);
	}

	@Test
	public void testFailedMessageTranslationWithoutInsertions()
	{
		String messageID = "non.existing.message.id";
		String expectedResult = messageID;
		assertExceptionTranslation(expectedResult, messageID);
	}

	// @Test
	public void testSuccessfulMessageTranslationWithInsertions()
	{
		String messageId = "error.sms.sending.failed.httpStatus";
		String expectedResult = "Unexpected status code received from SMS service: first. Message: second";
		assertExceptionTranslation(expectedResult, messageId, "first", "second");
	}

	@Test
	public void testFailedMessageTranslationWithInsertions()
	{
		String messageID = "non.existing.message.id";
		String expectedResult = messageID + "; parameters: \"first\", \"second\"";
		assertExceptionTranslation(expectedResult, messageID, "first", "second");
	}

	private void assertExceptionTranslation(String expectedResult, String messageId, Object... parameters)
	{
		TestException exception = new TestException(messageId, parameters);
		assertThat(exception.getMessage(), equalTo(expectedResult));
		assertThat(exception.getLocalizedMessage(), equalTo(expectedResult));
	}
}
