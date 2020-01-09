/*******************************************************************************
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.jobs;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import nu.yona.server.Constants;

class ErrorLoggingListener implements JobExecutionListener
{
	private static final Logger logger = LoggerFactory.getLogger(ErrorLoggingListener.class);

	@Override
	public void beforeJob(JobExecution jobExecution)
	{
		// Nothing to do here
	}

	@Override
	public void afterJob(JobExecution jobExecution)
	{
		if (jobExecution.getStatus() == BatchStatus.FAILED)
		{
			List<Throwable> failureExceptions = jobExecution.getAllFailureExceptions();
			String jobName = jobExecution.getJobInstance().getJobName();
			logger.error(Constants.ALERT_MARKER,
					"Fatal error: Batch job '" + jobName + "' failed with " + failureExceptions.size() + " failure exceptions");
			failureExceptions.forEach(e -> logger.error("Batch job '" + jobName + "' failed with exception", e));
		}
	}
}
