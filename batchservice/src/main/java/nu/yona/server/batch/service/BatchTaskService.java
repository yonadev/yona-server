/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.service;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import nu.yona.server.batch.client.PinResetConfirmationCodeSendRequestDto;
import nu.yona.server.batch.client.SystemMessageSendRequestDto;
import nu.yona.server.batch.quartz.SchedulingService;
import nu.yona.server.batch.quartz.SchedulingService.ScheduleGroup;
import nu.yona.server.batch.quartz.jobs.PinResetConfirmationCodeSenderQuartzJob;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.TimeUtil;

@Service
public class BatchTaskService
{
	private static final String PIN_RESET_CONFIRMMATION_CODE_JOB_NAME = "PinResetConfirmationCode";
	private static final String PIN_RESET_CONFIRMMATION_CODE_TRIGGER_NAME = PIN_RESET_CONFIRMMATION_CODE_JOB_NAME;

	private static final Logger logger = LoggerFactory.getLogger(BatchTaskService.class);

	@Autowired
	private SchedulingService schedulingService;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	@Qualifier("activityAggregationJob")
	private Job activityAggregationJob;

	@Autowired
	@Qualifier("sendSystemMessageJob")
	private Job sendSystemMessageJob;

	public ActivityAggregationBatchJobResultDto aggregateActivities()
	{
		logger.info("Triggering activity aggregation");
		JobParameters jobParameters = new JobParametersBuilder().addDate("uniqueInstanceId", new Date()).toJobParameters();
		// NOTICE: executes the job synchronously, on purpose, because the tests rely on this (they assert on job execution
		// results) and this is normally not scheduled manually
		JobExecution jobExecution = launchImmediatelySynchronously(activityAggregationJob, jobParameters);
		jobExecution.getStepExecutions().forEach(
				e -> logger.info("Step {} read {} entities and wrote {}", e.getStepName(), e.getReadCount(), e.getWriteCount()));
		return ActivityAggregationBatchJobResultDto.createInstance(jobExecution);
	}

	public void requestPinResetConfirmationCode(PinResetConfirmationCodeSendRequestDto request)
	{
		logger.info("Received request to generate PIN reset confirmation code for user with ID {} at {}", request.getUserId(),
				request.getExecutionTime());
		schedulingService.schedule(ScheduleGroup.OTHER, PIN_RESET_CONFIRMMATION_CODE_JOB_NAME,
				PIN_RESET_CONFIRMMATION_CODE_TRIGGER_NAME + " " + request.getUserId(),
				PinResetConfirmationCodeSenderQuartzJob.buildParameterMap(request.getUserId(), request.getLocaleString()),
				TimeUtil.toDate(request.getExecutionTime()));
	}

	public void sendSystemMessage(SystemMessageSendRequestDto request)
	{
		logger.info("Received request to send system message with text {}", request.getMessageText());

		JobParameters jobParameters = new JobParametersBuilder().addDate("uniqueInstanceId", new Date())
				.addString("messageText", request.getMessageText()).toJobParameters();
		launchImmediately(sendSystemMessageJob, jobParameters);
	}

	private JobExecution launchImmediately(Job job, JobParameters jobParameters)
	{
		return launchImmediately(new SimpleAsyncTaskExecutor(), job, jobParameters);
	}

	private JobExecution launchImmediatelySynchronously(Job job, JobParameters jobParameters)
	{
		// ONLY FOR TEST PURPOSES
		return launchImmediately(new SyncTaskExecutor(), job, jobParameters);
	}

	private JobExecution launchImmediately(TaskExecutor taskExecutor, Job job, JobParameters jobParameters)
	{
		try
		{
			SimpleJobLauncher launcher = new SimpleJobLauncher();
			launcher.setJobRepository(jobRepository);
			launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());
			return launcher.run(job, jobParameters);
		}
		catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
				| JobParametersInvalidException e)
		{
			logger.error("Unexpected exception", e);
			throw YonaException.unexpected(e);
		}
	}
}
