/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.jobs;

import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.PinResetRequestService;
import nu.yona.server.subscriptions.service.UserService;

@Component
public class PinResetConfirmationCodeSenderBatchJob
{
	private static final int CHUNK_SIZE = 10; // This is actually nonsense, as the ID is unique

	private static final Logger logger = LoggerFactory.getLogger(PinResetConfirmationCodeSenderBatchJob.class);

	@Autowired
	private UserService userService;

	@Autowired
	private PinResetRequestService pinResetRequestService;

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private JpaPagingItemReader<User> reader;

	@Autowired
	private ItemProcessor<User, User> processor;

	@Bean(destroyMethod = "")
	@StepScope
	public JpaPagingItemReader<User> reader(@Value("#{jobParameters[userId]}") String userId)
	{
		try
		{
			String jpqlQuery = "SELECT u FROM User u WHERE u.id = :userId";

			JpaPagingItemReader<User> reader = new JpaPagingItemReader<>();
			reader.setQueryString(jpqlQuery);
			reader.setParameterValues(Collections.singletonMap("userId", UUID.fromString(userId)));
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
	@StepScope
	public ItemProcessor<User, User> processor(@Value("#{jobParameters[locale]}") String localeString)
	{
		return new ItemProcessor<User, User>() {
			@Override
			public User process(User user) throws Exception
			{
				logger.info("Generating pin reset confirmation code for user with mobile number '{}' and ID '{}'",
						user.getMobileNumber(), user.getId());
				LocaleContextHolder.setLocale(Locale.forLanguageTag(localeString));
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
		return stepBuilderFactory.get("step1").<User, User> chunk(CHUNK_SIZE).reader(reader).processor(processor)
				.writer(writer()).build();
	}

	@Bean("pinResetConfirmationCodeSenderJob")
	public Job pinResetConfirmationCodeSenderJob()
	{
		return jobBuilderFactory.get("pinResetConfirmationCodeSenderJob").incrementer(new RunIdIncrementer()).flow(step1()).end()
				.build();
	}
}
