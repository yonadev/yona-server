/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.templates;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import nu.yona.server.ThymeleafConfiguration;
import nu.yona.server.Translator;
import nu.yona.server.util.ThymeleafUtil;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { ThymeleafConfiguration.class })
public class TemplateTest
{
	@Autowired
	@Qualifier("smsTemplateEngine")
	private TemplateEngine smsTemplateEngine;

	@Autowired
	@Qualifier("emailTemplateEngine")
	private TemplateEngine emailTemplateEngine;

	@Autowired
	@Qualifier("otherTemplateEngine")
	private TemplateEngine otherTemplateEngine;

	@Before
	public void setUp()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@Test
	public void testAppleAppSiteAssociation()
	{
		Context ctx = ThymeleafUtil.createContext();
		String testAppleAppId = "ADummyTestId";
		ctx.setVariable("appleAppId", testAppleAppId);

		String result = otherTemplateEngine.process("apple-app-site-association.json", ctx);
		assertThat(result, containsString("\"appID\": \"" + testAppleAppId + "\","));
	}

	@Test
	public void testSms()
	{
		String requestingUserFirstName = "john";
		String emailAddress = "a@b.c";
		String result = buildSms(Optional.empty(), requestingUserFirstName, emailAddress);
		String expectedResult = MessageFormat.format("You have been invited to Yona by {0}. Please check your email at {1}!",
				requestingUserFirstName, emailAddress);
		assertThat(result, equalTo(expectedResult));
	}

	@Test
	public void testDutchSms()
	{
		String requestingUserFirstName = "john";
		String emailAddress = "a@b.c";
		String result = buildSms(Optional.of(Locale.forLanguageTag("nl-NL")), requestingUserFirstName, emailAddress);
		String expectedResult = MessageFormat.format("{0} heeft je uitgenodigd voor Yona. Zie je mail op {1}!",
				requestingUserFirstName, emailAddress);
		assertThat(result, equalTo(expectedResult));
	}

	private String buildSms(Optional<Locale> locale, String requestingUserFirstName, String emailAddress)
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("requestingUserFirstName", requestingUserFirstName);
		ctx.setVariable("emailAddress", emailAddress);
		locale.ifPresent(l -> ctx.setLocale(l));

		return smsTemplateEngine.process("buddy-invitation.txt", ctx);
	}

	@Test
	public void testEmailSubject()
	{
		String result = buildEmailSubject(Optional.empty());
		String expectedResult = "Become my friend on Yona!";
		assertThat(result, equalTo(expectedResult));
	}

	@Test
	public void testDutchEmailSubject()
	{
		String result = buildEmailSubject(Optional.of(Locale.forLanguageTag("nl-NL")));
		String expectedResult = "Word vriend op Yona!";
		assertThat(result, equalTo(expectedResult));
	}

	private String buildEmailSubject(Optional<Locale> locale)
	{
		Context ctx = ThymeleafUtil.createContext();
		locale.ifPresent(l -> ctx.setLocale(l));

		return emailTemplateEngine.process("buddy-invitation-subject.txt", ctx);
	}

	@Test
	public void testEmailBody()
	{
		String buddyFirstName = "Richard";
		String buddyLastName = "Quin";
		String inviteUrl = "http://www.this.that?includePrivateData=false";
		String personalInvitationMessage = "Shall we?";
		String requestingUserFirstName = "Bob";
		String requestingUserLastName = "Dunn";
		String requestingUserMobileNumber = "+31687654321";
		String requestingUserNickname = "BD";
		String result = buildEmailBuddy(Optional.empty(), buddyFirstName, buddyLastName, inviteUrl, personalInvitationMessage,
				requestingUserFirstName, requestingUserLastName, requestingUserMobileNumber, requestingUserNickname);

		String expectedLinkFragment = MessageFormat.format("<a href=\"{0}\"", inviteUrl);
		assertThat(result, containsString(expectedLinkFragment));

		String expectedWarningLine = MessageFormat.format(
				"<strong>Important</strong>: Verify if the invitation is really from {0} {1} and check the mobile number: <a href=\"tel:{2}\" style=\"color: #2678bf; text-decoration: none;\">{2}</a>.",
				requestingUserFirstName, requestingUserLastName, requestingUserMobileNumber);
		assertThat(result, containsString(expectedWarningLine));
		assertThat(result, containsString("Open the app and &ldquo;join&rdquo;."));

		String expectedHeaderImageUrl = "https://app.prd.yona.nu/media/img/en_US/header.jpg";
		assertThat(result, containsString(expectedHeaderImageUrl));
	}

	@Test
	public void testDutchEmailBody()
	{
		String buddyFirstName = "Richard";
		String buddyLastName = "Quin";
		String inviteUrl = "http://www.this.that?includePrivateData=false";
		String personalInvitationMessage = "Shall we?";
		String requestingUserFirstName = "Bob";
		String requestingUserLastName = "Dunn";
		String requestingUserMobileNumber = "+31687654321";
		String requestingUserNickname = "BD";
		String result = buildEmailBuddy(Optional.of(Locale.forLanguageTag("nl-NL")), buddyFirstName, buddyLastName, inviteUrl,
				personalInvitationMessage, requestingUserFirstName, requestingUserLastName, requestingUserMobileNumber,
				requestingUserNickname);

		String expectedLinkFragment = MessageFormat.format("<a href=\"{0}\"", inviteUrl);
		assertThat(result, containsString(expectedLinkFragment));

		String expectedWarningLine = MessageFormat.format(
				"<strong>Belangrijk</strong>: Let op of de uitnodiging werkelijk van {0} {1} komt en check het mobiele nummer: <a href=\"tel:{2}\" style=\"color: #2678bf; text-decoration: none;\">{2}</a>.",
				requestingUserFirstName, requestingUserLastName, requestingUserMobileNumber);
		assertThat(result, containsString(expectedWarningLine));

		String expectedHeaderImageUrl = "https://app.prd.yona.nu/media/img/nl_NL/header.jpg";
		assertThat(result, containsString(expectedHeaderImageUrl));
	}

	private String buildEmailBuddy(Optional<Locale> locale, String buddyFirstName, String buddyLastName, String inviteUrl,
			String personalInvitationMessage, String requestingUserFirstName, String requestingUserLastName,
			String requestingUserMobileNumber, String requestingUserNickname)
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("buddyFirstName", buddyFirstName);
		ctx.setVariable("buddyLastName", buddyLastName);
		ctx.setVariable("inviteUrl", inviteUrl);
		ctx.setVariable("personalInvitationMessage", personalInvitationMessage);
		ctx.setVariable("requestingUserFirstName", requestingUserFirstName);
		ctx.setVariable("requestingUserLastName", requestingUserLastName);
		ctx.setVariable("requestingUserMobileNumber", requestingUserMobileNumber);
		ctx.setVariable("requestingUserNickname", requestingUserNickname);
		locale.ifPresent(l -> ctx.setLocale(l));
		ctx.setVariable("includedMediaBaseUrl", "https://app.prd.yona.nu/media/");

		return emailTemplateEngine.process("buddy-invitation-body.html", ctx);
	}
}