/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import nu.yona.server.util.ThymeleafUtil;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { ThymeleafConfiguration.class })
public class ThymeleafConfigurationTest
{
	private static Locale originalLocale;

	@Autowired
	@Qualifier("smsTemplateEngine")
	private TemplateEngine smsTemplateEngine;

	@Autowired
	@Qualifier("emailTemplateEngine")
	private TemplateEngine emailTemplateEngine;

	@Autowired
	@Qualifier("otherTemplateEngine")
	private TemplateEngine otherTemplateEngine;

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
	public void otherTemplateEngine_processAppleAppSiteAssociationJson_containsCorrectlyFormattedAppId()
	{
		Context ctx = ThymeleafUtil.createContext();
		String testAppleAppId = "ADummyTestId";
		ctx.setVariable("appleAppId", testAppleAppId);

		String result = otherTemplateEngine.process("apple-app-site-association.json", ctx);

		assertThat(result, containsString("\"appID\": \"" + testAppleAppId + "\","));
	}

	@Test
	public void smsTemplateEngine_processBuddyInvitationDefault_defaultTemplateFoundAndExpanded()
	{
		String requestingUserFirstName = "john";
		String emailAddress = "a@b.c";

		String result = buildSms(Optional.empty(), requestingUserFirstName, emailAddress);

		assertThat(result, equalTo("You have been invited to Yona by john. Please check your email at a@b.c!"));
	}

	@Test
	public void smsTemplateEngine_processBuddyInvitationDutch_dutchTemplateFoundAndExpanded()
	{
		String requestingUserFirstName = "john";
		String emailAddress = "a@b.c";

		String result = buildSms(Optional.of(Locale.forLanguageTag("nl-NL")), requestingUserFirstName, emailAddress);

		assertThat(result, equalTo("john heeft je uitgenodigd voor Yona. Zie je mail op a@b.c!"));
	}

	private String buildSms(Optional<Locale> locale, String requestingUserFirstName, String emailAddress)
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("requestingUserFirstName", requestingUserFirstName);
		ctx.setVariable("emailAddress", emailAddress);
		locale.ifPresent(ctx::setLocale);

		return smsTemplateEngine.process("buddy-invitation.txt", ctx);
	}

	@Test
	public void emailTemplateEngine_processBuddyInvitationSubjectDefault_defaultTemplateFoundAndExpanded()
	{
		String result = buildEmailSubject(Optional.empty(), "John Doe");

		assertThat(result, equalTo("Become friend of John Doe on Yona!"));
	}

	@Test
	public void emailTemplateEngine_processBuddyInvitationSubjectDutch_dutchTemplateFoundAndExpanded()
	{
		String result = buildEmailSubject(Optional.of(Locale.forLanguageTag("nl-NL")), "John Doe");

		assertThat(result, equalTo("Word vriend van John Doe op Yona!"));
	}

	private String buildEmailSubject(Optional<Locale> locale, String requestingUserFullName)
	{
		Context ctx = ThymeleafUtil.createContext();
		locale.ifPresent(ctx::setLocale);
		ctx.setVariable("requestingUserFullName", requestingUserFullName);

		return emailTemplateEngine.process("buddy-invitation-subject.txt", ctx);
	}

	@Test
	public void emailTemplateEngine_processBuddyInvitationBodyDefault_defaultTemplateFoundAndExpanded()
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

		assertThat(result, containsString(MessageFormat.format("<a href=\"{0}\"", inviteUrl)));
		assertThat(result, containsString(MessageFormat.format(
				"<strong>Important</strong>: Verify if the invitation is really from {0} {1} and check the mobile number: <a href=\"tel:{2}\" style=\"color: #2678bf; text-decoration: none;\">{2}</a>.",
				requestingUserFirstName, requestingUserLastName, requestingUserMobileNumber)));
		assertThat(result, containsString("Return to this mail and click <a href=\"http"));
		assertThat(result, containsString("https://app.prd.yona.nu/media/img/en_US/header.jpg"));
	}

	@Test
	public void emailTemplateEngine_processBuddyInvitationBodyDutch_dutchTemplateFoundAndExpanded()
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

		assertThat(result, containsString(MessageFormat.format("<a href=\"{0}\"", inviteUrl)));
		assertThat(result, containsString(MessageFormat.format(
				"<strong>Belangrijk</strong>: Let op of de uitnodiging werkelijk van {0} {1} komt en check het mobiele nummer: <a href=\"tel:{2}\" style=\"color: #2678bf; text-decoration: none;\">{2}</a>.",
				requestingUserFirstName, requestingUserLastName, requestingUserMobileNumber)));
		assertThat(result, containsString("Ga terug naar deze mail en klik op <a href=\"http"));
		assertThat(result, containsString("https://app.prd.yona.nu/media/img/nl_NL/header.jpg"));
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
		ctx.setVariable("includedMediaBaseUrl", "https://app.prd.yona.nu/media/");
		locale.ifPresent(l -> ctx.setLocale(l));

		return emailTemplateEngine.process("buddy-invitation-body.html", ctx);
	}
}
