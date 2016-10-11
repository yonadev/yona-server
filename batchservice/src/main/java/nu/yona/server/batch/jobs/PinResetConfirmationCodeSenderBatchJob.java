/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.jobs;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;

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
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import nu.yona.server.Translator;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.PinResetRequestService;
import nu.yona.server.subscriptions.service.UserService;

@Component
public class PinResetConfirmationCodeSenderBatchJob
{
	private static final int CHUNK_SIZE = 10;

	private static final Logger logger = LoggerFactory.getLogger(PinResetConfirmationCodeSenderBatchJob.class);

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private UserService userService;

	@Autowired
	private PinResetRequestService pinResetRequestService;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private JpaPagingItemReader<User> reader;

	@Autowired
	private TaskScheduler scheduler;

	@Bean(destroyMethod = "")
	@StepScope
	public JpaPagingItemReader<User> reader(@Value("#{jobParameters[tillDate]}") final Date tillDate)
	{
		try
		{
			String jpqlQuery = "SELECT u FROM User u WHERE u.pinResetConfirmationCode IS NOT NULL AND u.pinResetConfirmationCode.confirmationCode IS NULL AND u.pinResetConfirmationCode.creationTime < :tillDate";

			JpaPagingItemReader<User> reader = new JpaPagingItemReader<User>();
			reader.setQueryString(jpqlQuery);
			reader.setParameterValues(Collections.singletonMap("tillDate", toZonedDateTime(tillDate)));
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
				logger.info("Generating pin reset confirmation code for user with mobile number '{}' and ID '{}'",
						user.getMobileNumber(), user.getID());
				LocaleContextHolder.setLocale(Translator.EN_US_LOCALE); // TODO: Make this the user's locale
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
	public Step step1()
	{
		return stepBuilderFactory.get("step1").<User, User> chunk(CHUNK_SIZE).reader(reader).processor(processor())
				.writer(writer()).build();
	}

	@Bean
	public Job pinResetConfirmationCodeSenderJob()
	{
		return jobBuilderFactory.get("pinResetConfirmationCodeSenderJob").incrementer(new RunIdIncrementer()).flow(step1()).end()
				.build();
	}

	/*
	 * This method is created as alternative to the Spring expression annotation on runJob, as that is not supported yet.
	 */
	@EventListener({ ContextRefreshedEvent.class })
	void onContextStarted(ContextRefreshedEvent event)
	{
		scheduler.scheduleWithFixedDelay(this::runJob,
				yonaProperties.getBatch().getPinResetRequestConfirmationCodeInterval().toMillis());
	}

	// TODO SPR-13625 @Scheduled(fixedRateString =
	// "#{T(java.time.Duration).parse('${yona.batch.pinResetRequestConfirmationCodeInterval}').toMillis()}")
	private void runJob()
	{
		try
		{
			SimpleJobLauncher launcher = new SimpleJobLauncher();
			launcher.setJobRepository(jobRepository);
			launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());

			Date tillDate = toDate(
					ZonedDateTime.now().plus(yonaProperties.getBatch().getPinResetRequestConfirmationCodeInterval())
							.minus(yonaProperties.getSecurity().getPinResetRequestConfirmationCodeDelay()));
			JobParameters jobParameters = new JobParametersBuilder().addDate("tillDate", tillDate).toJobParameters();
			launcher.run(pinResetConfirmationCodeSenderJob(), jobParameters);
		}
		catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
				| JobParametersInvalidException e)
		{
			logger.error("Unexpected exception", e);
			throw YonaException.unexpected(e);
		}
	}

	private static ZonedDateTime toZonedDateTime(final Date date)
	{
		return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
	}

	private static Date toDate(final ZonedDateTime zonedDateTime)
	{
		return Date.from(zonedDateTime.toInstant());
	}
}