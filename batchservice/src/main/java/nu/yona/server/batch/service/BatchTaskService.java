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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import nu.yona.server.batch.client.PinResetConfirmationCodeSendRequestDto;
import nu.yona.server.batch.quartz.SchedulingService;
import nu.yona.server.batch.quartz.SchedulingService.ScheduleGroup;
import nu.yona.server.batch.quartz.jobs.PinResetConfirmationCodeSenderQuartzJob;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.TimeUtil;

@Service
public class BatchTaskService
{
	private static final String JOB_NAME = "PinResetConfirmationCode";

	private static final Logger logger = LoggerFactory.getLogger(BatchTaskService.class);

	@Autowired
	private SchedulingService schedulingService;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	@Qualifier("activityAggregationJob")
	private Job activityAggregationJob;

	@Scheduled(cron = "${yona.batchService.activityAggregationJobCronExpression}")
	public ActivityAggregationBatchJobResultDto aggregateActivities()
	{
		try
		{
			logger.info("Triggering activity aggregation");
			SimpleJobLauncher launcher = new SimpleJobLauncher();
			launcher.setJobRepository(jobRepository);
			launcher.setTaskExecutor(new SyncTaskExecutor()); // NOTICE: executes the job synchronously on purpose

			JobParameters jobParameters = new JobParametersBuilder().addDate("uniqueInstanceId", new Date()).toJobParameters();
			JobExecution jobExecution = launcher.run(activityAggregationJob, jobParameters);
			jobExecution.getStepExecutions().forEach(e -> logger.info("Step {} read {} entities and wrote {}", e.getStepName(),
					e.getReadCount(), e.getWriteCount()));
			return ActivityAggregationBatchJobResultDto.createInstance(jobExecution);
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
		schedulingService.schedule(ScheduleGroup.OTHER, JOB_NAME, JOB_NAME + " " + request.getUserId(),
				PinResetConfirmationCodeSenderQuartzJob.buildParameterMap(request.getUserId(), request.getLocaleString()),
				TimeUtil.toDate(request.getExecutionTime()));
	}
}
