/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.jobs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.SystemMessage;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.rest.RestUtil;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;

@Component
public class SendSystemMessageBatchJob
{
	private static final Logger logger = LoggerFactory.getLogger(SendSystemMessageBatchJob.class);

	private static final int USERS_CHUNK_SIZE = 50;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	@Qualifier("sendSystemMessageJobUserAnonymizedReader")
	private ItemReader<UUID> userAnonymizedReader;

	@Autowired
	@Qualifier("sendSystemMessageJobUserAnonymizedProcessor")
	private ItemProcessor<UUID, Void> userAnonymizedProcessor;

	@Autowired
	@Qualifier("noopItemWriter")
	private ItemWriter<Object> noopItemWriter;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	@Autowired
	private MessageService messageService;

	@Bean("sendSystemMessageJob")
	public Job sendSystemMessagesBatchJob()
	{
		return new JobBuilder("sendSystemMessagesBatchJob", jobRepository).listener(new ErrorLoggingListener())
				.incrementer(new RunIdIncrementer()).flow(sendSystemMessages()).end().build();
	}

	private Step sendSystemMessages()
	{
		return new StepBuilder("sendSystemMessages", jobRepository).<UUID, Void>chunk(USERS_CHUNK_SIZE, transactionManager)
				.reader(userAnonymizedReader).processor(userAnonymizedProcessor).writer(noopItemWriter).build();
	}

	@Bean(name = "sendSystemMessageJobUserAnonymizedReader", destroyMethod = "")
	@StepScope
	public ItemReader<UUID> userAnonymizedReader()
	{
		return userAnonymizedIdReader();
	}

	@Bean(name = "sendSystemMessageJobUserAnonymizedProcessor", destroyMethod = "")
	@StepScope
	private ItemProcessor<UUID, Void> userAnonymizedProcessor()
	{
		return new ItemProcessor<UUID, Void>()
		{
			@Value("#{jobParameters['messageText']}")
			private String messageText;

			@Override
			public Void process(UUID userAnonymizedId) throws Exception
			{
				logger.debug("Processing user anonymized with id {}", userAnonymizedId);
				UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);

				SystemMessage message = SystemMessage.createInstance(messageText);
				messageService.sendMessage(message, userAnonymized);

				return null;
			}
		};
	}

	private ItemReader<UUID> userAnonymizedIdReader()
	{
		SqlPagingQueryProviderFactoryBean sqlPagingQueryProviderFactoryBean = createQueryProviderFactory();
		JdbcPagingItemReader<UUID> reader = createReader(sqlPagingQueryProviderFactoryBean);
		logger.info("Reading users anonymized in chunks of {}", USERS_CHUNK_SIZE);
		return reader;
	}

	private SqlPagingQueryProviderFactoryBean createQueryProviderFactory()
	{
		SqlPagingQueryProviderFactoryBean sqlPagingQueryProviderFactoryBean = new SqlPagingQueryProviderFactoryBean();
		sqlPagingQueryProviderFactoryBean.setDataSource(dataSource);
		sqlPagingQueryProviderFactoryBean.setSelectClause("select id");
		sqlPagingQueryProviderFactoryBean.setFromClause("from users_anonymized");
		sqlPagingQueryProviderFactoryBean.setSortKey("id");
		return sqlPagingQueryProviderFactoryBean;
	}

	private JdbcPagingItemReader<UUID> createReader(SqlPagingQueryProviderFactoryBean sqlPagingQueryProviderFactoryBean)
	{
		try
		{
			JdbcPagingItemReader<UUID> reader = new JdbcPagingItemReader<>();
			reader.setQueryProvider(sqlPagingQueryProviderFactoryBean.getObject());
			reader.setDataSource(dataSource);
			reader.setPageSize(USERS_CHUNK_SIZE);
			reader.setRowMapper(singleUUIDColumnRowMapper());
			reader.afterPropertiesSet();
			reader.setSaveState(true);
			return reader;
		}
		catch (Exception e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private static RowMapper<UUID> singleUUIDColumnRowMapper()
	{
		return new RowMapper<UUID>()
		{

			@Override
			public UUID mapRow(ResultSet rs, int rowNum) throws SQLException
			{
				return RestUtil.parseUuid(rs.getString(1));
			}

		};
	}
}