package nu.yona.server.batch.service;

import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
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
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import nu.yona.server.batch.client.PinResetConfirmationCodeSendRequestDto;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.TimeUtil;

@Service
public class BatchTaskService
{
	private static final Logger logger = LoggerFactory.getLogger(BatchTaskService.class);

	@Autowired
	private TaskScheduler scheduler;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	@Qualifier("pinResetConfirmationCodeSenderJob")
	private Job pinResetConfirmationCodeSenderJob;

	@Autowired
	@Qualifier("activityAggregationJob")
	private Job activityAggregationJob;

	@Scheduled(cron = "${yona.batchService.activityAggregationJobCronExpression}")
	public void aggregateActivities()
	{
		try
		{
			logger.info("Triggering activity aggregation");
			SimpleJobLauncher launcher = new SimpleJobLauncher();
			launcher.setJobRepository(jobRepository);
			launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());

			JobParameters jobParameters = new JobParametersBuilder().addDate("uniqueInstanceId", new Date()).toJobParameters();
			launcher.run(activityAggregationJob, jobParameters);
		}
		catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
				| JobParametersInvalidException e)
		{
			logger.error("Unexpected exception", e);
			throw YonaException.unexpected(e);
		}
	}

	public void requestPinResetConfirmationCode(PinResetConfirmationCodeSendRequestDto request)
	{
		logger.info("Received request to generate PIN reset confirmation code for user with ID {} at {}", request.getUserId(),
				request.getExecutionTime());
		scheduler.schedule(() -> generateAndSendPinResetConfirmationCode(request.getUserId(), request.getLocaleString()),
				TimeUtil.toDate(request.getExecutionTime()));
	}

	private void generateAndSendPinResetConfirmationCode(UUID userId, String localeString)
	{
		try
		{
			logger.info("Triggering generation of PIN reset confirmation code for user with ID {}", userId);
			SimpleJobLauncher launcher = new SimpleJobLauncher();
			launcher.setJobRepository(jobRepository);
			launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());

			JobParameters jobParameters = new JobParametersBuilder().addString("userId", userId.toString())
					.addString("locale", localeString).addDate("uniqueInstanceId", new Date()).toJobParameters();
			launcher.run(pinResetConfirmationCodeSenderJob, jobParameters);
		}
		catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
				| JobParametersInvalidException e)
		{
			logger.error("Unexpected exception", e);
			throw YonaException.unexpected(e);
		}
	}
}
