/*
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

public class ApplicationStatusLogger extends AbstractFailureAnalyzer<Throwable>
{
	private static final Logger logger = LoggerFactory.getLogger(ApplicationStatusLogger.class);

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, Throwable cause)
	{
		logger.error(Constants.ALERT_MARKER, "Service failed to start. Cause: " + cause.getMessage(), rootFailure);
		return null; // We didn't really analyze something but only logged the issue
	}

	public static void addLoggerForContextClosedEvent(ConfigurableApplicationContext context)
	{
		context.addApplicationListener(new ApplicationListener<ContextClosedEvent>()
		{
			@Override
			public void onApplicationEvent(ContextClosedEvent event)
			{
				if (event.getApplicationContext() != context)
				{
					return;
				}
				logger.info(Constants.ALERT_MARKER, "Application closed");
			}
		});
	}
}