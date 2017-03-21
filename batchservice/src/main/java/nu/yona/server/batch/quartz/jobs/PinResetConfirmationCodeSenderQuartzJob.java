package nu.yona.server.batch.quartz.jobs;

import java.util.Date;
import java.util.UUID;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
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
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.YonaException;

@Service
public class PinResetConfirmationCodeSenderQuartzJob implements org.quartz.Job
{
	public static final String USER_ID_KEY = "userId";
	public static final String LOCALE_STRING_KEY = "localeString";

	private static final Logger logger = LoggerFactory.getLogger(PinResetConfirmationCodeSenderQuartzJob.class);

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	@Qualifier("pinResetConfirmationCodeSenderJob")
	private Job pinResetConfirmationCodeSenderJob;

	@Override
	public void execute(JobExecutionContext context)
	{
		try
		{
			JobDataMap jobData = context.getMergedJobDataMap();
			UUID userId = UUID.fromString((String) jobData.get(USER_ID_KEY));
			String localeString = (String) jobData.get(LOCALE_STRING_KEY);
			logger.info("Triggering generation of PIN reset confirmation code for user with ID {}", userId);
			SimpleJobLauncher launcher = new SimpleJobLauncher();
			launcher.setJobRepository(jobRepository);
			launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());

			JobParameters jobParameters = new JobParametersBuilder().addString(USER_ID_KEY, userId.toString())
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
