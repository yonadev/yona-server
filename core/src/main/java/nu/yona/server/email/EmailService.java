/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.email;

import java.util.Map;

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

import nu.yona.server.properties.YonaProperties;

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

	public void sendEmail(String senderName, InternetAddress receiverAddress, String subjectTemplateName, String bodyTemplateName,
			Map<String, Object> templateParameters)
	{
		logger.info("Sending e-mail to '{}'. subjectTemplateName: '{}'.", receiverAddress, subjectTemplateName);

		if (!yonaProperties.getEmail().isEnabled())
		{
			logger.info("E-mail sending is disabled. No message has been sent.");
			return;
		}

		MimeMessagePreparator preparator = new MimeMessagePreparator() {
			@Override
			public void prepare(MimeMessage mimeMessage) throws Exception
			{
				Context ctx = new Context();
				templateParameters.entrySet().stream().forEach(e -> ctx.setVariable(e.getKey(), e.getValue()));

				String subjectText = emailTemplateEngine.process(subjectTemplateName + ".txt", ctx);
				String bodyText = emailTemplateEngine.process(bodyTemplateName + ".html", ctx);

				MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
				message.setFrom(new InternetAddress(yonaProperties.getEmail().getSenderAddress(), senderName));
				message.setTo(receiverAddress);
				message.setSubject(subjectText);
				message.setText(bodyText, true);
			}
		};
		mailSender.send(preparator);

		logger.info("E-mail sent succesfully.");
	}
}
