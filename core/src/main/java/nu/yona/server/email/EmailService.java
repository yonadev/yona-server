/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.email;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.ThymeleafUtil;

@Service
public class EmailService
{
	private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private JavaMailSender mailSender;
	@Autowired
	@Qualifier("emailTemplateEngine")
	private TemplateEngine emailTemplateEngine;
	private EmailDto lastEmail;

	public void sendEmail(String senderName, InternetAddress receiverAddress, String subjectTemplateName, String bodyTemplateName,
			Map<String, Object> templateParameters)
	{
		logger.info("Sending e-mail to '{}'. subjectTemplateName: '{}'.", receiverAddress, subjectTemplateName);

		MimeMessagePreparator preparator = mimeMessage -> prepareMimeMessage(mimeMessage, senderName, receiverAddress,
				subjectTemplateName, bodyTemplateName, templateParameters);
		if (yonaProperties.getEmail().isEnabled())
		{
			try
			{
				logger.info("About to call mailSender.send");
				mailSender.send(preparator);
			}
			finally
			{
				logger.info("mailSender.send completed, possibly with an exception");
			}
			logger.info("E-mail sent succesfully.");
		}
		else
		{
			logger.info("E-mail sending is disabled. No message has been sent.");
			lastEmail = EmailDto.createInstance(mailSender, preparator);
		}
	}

	private void prepareMimeMessage(MimeMessage mimeMessage, String senderName, InternetAddress receiverAddress,
			String subjectTemplateName, String bodyTemplateName, Map<String, Object> templateParameters)
			throws MessagingException, UnsupportedEncodingException
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("includedMediaBaseUrl", yonaProperties.getEmail().getIncludedMediaBaseUrl());
		ctx.setVariable("appleAppStoreUrl", yonaProperties.getEmail().getAppleAppStoreUrl());
		ctx.setVariable("googlePlayStoreUrl", yonaProperties.getEmail().getGooglePlayStoreUrl());
		templateParameters.entrySet().stream().forEach(e -> ctx.setVariable(e.getKey(), e.getValue()));

		String subjectText = emailTemplateEngine.process(subjectTemplateName + ".txt", ctx);
		String bodyText = emailTemplateEngine.process(bodyTemplateName + ".html", ctx);

		MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
		message.setFrom(new InternetAddress(yonaProperties.getEmail().getSenderAddress(), senderName));
		message.setTo(receiverAddress);
		message.setSubject(subjectText);
		message.setText(bodyText, true);
	}

	/**
	 * Returns the last sent email, provided the service was configured for testing. In the production configuration, the last
	 * email is not retained.
	 *
	 * @return the last sent email, provided the service was configured for testing. Null otherwise.
	 */
	public EmailDto getLastEmail()
	{
		return lastEmail;
	}

	public static class EmailDto
	{
		private final String from;
		private final String to;
		private final String subject;
		private final String body;

		public EmailDto(String from, String to, String subject, String body)
		{
			this.from = from;
			this.to = to;
			this.subject = subject;
			this.body = body;
		}

		public static EmailDto createInstance(JavaMailSender mailSender, MimeMessagePreparator preparator)
		{
			try
			{
				MimeMessage mimeMessage = mailSender.createMimeMessage();
				preparator.prepare(mimeMessage);
				String from = mimeMessage.getFrom()[0].toString();
				String to = mimeMessage.getRecipients(Message.RecipientType.TO)[0].toString();
				String subject = mimeMessage.getSubject();
				String body = mimeMessage.getContent().toString();
				return new EmailDto(from, to, subject, body);
			}
			catch (Exception e)
			{
				throw YonaException.unexpected(e);
			}
		}

		public String getFrom()
		{
			return from;
		}

		public String getTo()
		{
			return to;
		}

		public String getSubject()
		{
			return subject;
		}

		public String getBody()
		{
			return body;
		}
	}
}
