/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz.jobs;

import java.io.UnsupportedEncodingException;
import java.util.Collections;

import javax.mail.internet.InternetAddress;

import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.email.EmailService;
import nu.yona.server.exceptions.EmailException;
import nu.yona.server.properties.YonaProperties;

@Service
public class EmailTestQuartzJob implements org.quartz.Job
{
	@Autowired
	private EmailService emailService;

	@Autowired
	private YonaProperties yonaProperties;

	private static final Logger logger = LoggerFactory.getLogger(EmailTestQuartzJob.class);

	@Override
	public void execute(JobExecutionContext context)
	{
		try
		{
			if (yonaProperties.getEmail().isEnabled())
			{
				emailService.sendEmail("dummy", new InternetAddress(yonaProperties.getEmail().getTestEmailAddress(), "dummy"),
						"buddy-invitation-subject", "buddy-invitation-body", Collections.emptyMap());
				logger.info("EmailTestQuartzJob: Test e-mail sent succesfully.");
			}
			else
			{
				logger.info("EmailTestQuartzJob: E-mail sending is disabled, so nothing is sent.");
			}

		}
		catch (UnsupportedEncodingException e)
		{
			throw EmailException.emailSendingFailed(e);
		}
	}
}
