/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
		Context ctx = new Context();
		String testAppleAppId = "ADummyTestId";
		ctx.setVariable("appleAppId", testAppleAppId);

		String result = otherTemplateEngine.process("apple-app-site-association.json", ctx);
		assertThat(result, containsString("\"appID\": \"" + testAppleAppId + "\","));
	}

	@Test
	public void testAppleMobileConfig()
	{
		Context ctx = new Context();
		String ldapUsername = "DummyLdapUserName";
		String ldapPassword = "DummyLdapPassword";
		ctx.setVariable("ldapUsername", ldapUsername);
		ctx.setVariable("ldapPassword", ldapPassword);

		String result = otherTemplateEngine.process("apple.mobileconfig.xml", ctx);
		assertThat(result, containsString("<string>" + ldapUsername + "\\n" + ldapPassword + "</string>"));
	}

	@Test
	public void testSms()
	{
		String requestingUserName = "john";
		String emailAddress = "a@b.c";
		String result = buildSms(Optional.empty(), requestingUserName, emailAddress);
		String expectedResult = MessageFormat.format("You have been invited to Yona by {0}. Please check your email at {1}!",
				requestingUserName, emailAddress);
		assertThat(result, equalTo(expectedResult));
	}

	@Test
	public void testDutchSms()
	{
		String requestingUserName = "john";
		String emailAddress = "a@b.c";
		String result = buildSms(Optional.of(Locale.forLanguageTag("nl-NL")), requestingUserName, emailAddress);
		String expectedResult = MessageFormat.format("{0} heeft je uitgenodigd voor Yona. Zie je mail op {1}!",
				requestingUserName, emailAddress);
		assertThat(result, equalTo(expectedResult));
	}

	private String buildSms(Optional<Locale> locale, String requestingUserName, String emailAddress)
	{
		Context ctx = new Context();
		ctx.setVariable("requestingUserName", requestingUserName);
		ctx.setVariable("emailAddress", emailAddress);
		locale.ifPresent(l -> ctx.setLocale(l));

		return smsTemplateEngine.process("buddy-invitation.txt", ctx);
	}

	@Test
	public void testEmailSubject()
	{
		String requestingUserName = "John";
		String result = buildEmailSubject(Optional.empty(), requestingUserName);
		String expectedResult = MessageFormat.format("Yona buddy invitation from {0}", requestingUserName);
		assertThat(result, equalTo(expectedResult));
	}

	@Test
	public void testDutchEmailSubject()
	{
		String requestingUserName = "John";
		String result = buildEmailSubject(Optional.of(Locale.forLanguageTag("nl-NL")), requestingUserName);
		String expectedResult = MessageFormat.format("Yona vriendverzoek van {0}", requestingUserName);
		assertThat(result, equalTo(expectedResult));
	}

	private String buildEmailSubject(Optional<Locale> locale, String requestingUserName)
	{
		Context ctx = new Context();
		ctx.setVariable("requestingUserName", requestingUserName);
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

		String expectedLinkLine = MessageFormat.format("<p><a href=\"{0}\">{0}</a></p>", inviteUrl);
		assertThat(result, containsString(expectedLinkLine));

		String expectedGreetingLine = MessageFormat.format("<p>Best regards,<br/>{0} {1} | {2}</p>", requestingUserFirstName,
				requestingUserLastName, requestingUserNickname);
		assertThat(result, containsString(expectedGreetingLine));
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

		String expectedLinkLine = MessageFormat.format("<p><a href=\"{0}\">{0}</a></p>", inviteUrl);
		assertThat(result, containsString(expectedLinkLine));

		String expectedGreetingLine = MessageFormat.format("<p>Groetjes,<br/>{0} {1} | {2}</p>", requestingUserFirstName,
				requestingUserLastName, requestingUserNickname);
		assertThat(result, containsString(expectedGreetingLine));
	}

	private String buildEmailBuddy(Optional<Locale> locale, String buddyFirstName, String buddyLastName, String inviteUrl,
			String personalInvitationMessage, String requestingUserFirstName, String requestingUserLastName,
			String requestingUserMobileNumber, String requestingUserNickname)
	{
		Context ctx = new Context();
		ctx.setVariable("buddyFirstName", buddyFirstName);
		ctx.setVariable("buddyLastName", buddyLastName);
		ctx.setVariable("inviteUrl", inviteUrl);
		ctx.setVariable("personalInvitationMessage", personalInvitationMessage);
		ctx.setVariable("requestingUserFirstName", requestingUserFirstName);
		ctx.setVariable("requestingUserLastName", requestingUserLastName);
		ctx.setVariable("requestingUserMobileNumber", requestingUserMobileNumber);
		ctx.setVariable("requestingUserNickname", requestingUserNickname);
		locale.ifPresent(l -> ctx.setLocale(l));

		return emailTemplateEngine.process("buddy-invitation-body.html", ctx);
	}
}