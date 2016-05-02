package nu.yona.server.batch.sample;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.PinResetRequestService;
import nu.yona.server.subscriptions.service.UserService;

@Configuration
public class BatchConfiguration
{
	private static final int CHUNK_SIZE = 10;

	private static final Logger log = LoggerFactory.getLogger(BatchConfiguration.class);

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private UserService userService;

	@Autowired
	private PinResetRequestService pinResetRequestService;

	@Bean
	public JpaPagingItemReader<User> reader()
	{
		try
		{
			String jpqlQuery = "select u from User u where u.pinResetConfirmationCode IS NOT NULL and u.pinResetConfirmationCode.confirmationCode IS NULL";

			JpaPagingItemReader<User> reader = new JpaPagingItemReader<User>();
			reader.setQueryString(jpqlQuery);
			reader.setEntityManagerFactory(entityManager.getEntityManagerFactory());
			reader.setPageSize(CHUNK_SIZE);
			reader.afterPropertiesSet();
			reader.setSaveState(true);

			return reader;
		}
		catch (Exception e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	public ItemProcessor<User, User> processor()
	{
		return new ItemProcessor<User, User>() {
			@Override
			public User process(final User user) throws Exception
			{
				log.info("Generating pin reset confirmation code for user with mobilen number {}", user.getMobileNumber());
				ConfirmationCode pinResetConfirmationCode = user.getPinResetConfirmationCode();
				pinResetConfirmationCode.setConfirmationCode(userService.generateConfirmationCode());
				pinResetRequestService.sendConfirmationCodeTextMessage(user, pinResetConfirmationCode);

				return user;
			}

		};
	}

	@Bean
	public JpaItemWriter<User> writer()
	{
		JpaItemWriter<User> writer = new JpaItemWriter<>();
		writer.setEntityManagerFactory(entityManager.getEntityManagerFactory());

		return writer;
	}

	@Bean
	public Job importUserJob()
	{
		return jobBuilderFactory.get("importUserJob").incrementer(new RunIdIncrementer()).flow(step1()).end().build();
	}

	@Bean
	public Step step1()
	{
		TaskletStep step = stepBuilderFactory.get("step1").<User, User> chunk(CHUNK_SIZE).reader(reader()).processor(processor())
				.writer(writer()).build();
		return step;
	}

	@Scheduled(fixedRate = 5000)
	public void runJob()
	{
		try
		{
			SimpleJobLauncher launcher = new SimpleJobLauncher();
			launcher.setJobRepository(jobRepository);
			launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());
			JobParameters jobParameters = new JobParametersBuilder().addLong("startTime", System.currentTimeMillis())
					.toJobParameters();
			launcher.run(importUserJob(), jobParameters);
		}
		catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
				| JobParametersInvalidException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}