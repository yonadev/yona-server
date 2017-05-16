/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.jobs;

import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Component;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageDestinationRepository;
import nu.yona.server.messaging.entities.SystemMessage;

@Component
public class SendSystemMessagesBatchJob
{
	private static final Logger logger = LoggerFactory.getLogger(ActivityAggregationBatchJob.class);

	private static final int MESSAGE_DESTINATIONS_CHUNK_SIZE = 50;

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	@Qualifier("sendSystemMessagesJobMessageDestinationReader")
	private ItemReader<UUID> messageDestinationsReader;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private MessageDestinationRepository messageDestinationRepository;

	@Bean("sendSystemMessagesJob")
	public Job sendSystemMessagesBatchJob()
	{
		return jobBuilderFactory.get("sendSystemMessagesBatchJob").incrementer(new RunIdIncrementer()).flow(sendSystemMessages())
				.end().build();
	}

	private Step sendSystemMessages()
	{
		return stepBuilderFactory.get("sendSystemMessages").<UUID, MessageDestination> chunk(MESSAGE_DESTINATIONS_CHUNK_SIZE)
				.reader(messageDestinationsReader).processor(messageDestinationProcessor()).writer(messageDestinationWriter())
				.build();
	}

	@Bean(name = "sendSystemMessagesJobMessageDestinationReader", destroyMethod = "")
	@StepScope
	public ItemReader<UUID> messageDestinationReader()
	{
		return messageDestinationIdReader();
	}

	private ItemProcessor<UUID, MessageDestination> messageDestinationProcessor()
	{
		return new ItemProcessor<UUID, MessageDestination>() {
			@Override
			public MessageDestination process(UUID messageDestinationId) throws Exception
			{
				logger.debug("Processing message destination with id {}", messageDestinationId);
				MessageDestination destination = messageDestinationRepository.findOne(messageDestinationId);

				destination.send(SystemMessage.createInstance(messageText));

				return destination;
			}
		};
	}

	private JpaItemWriter<MessageDestination> messageDestinationWriter()
	{
		JpaItemWriter<MessageDestination> writer = new JpaItemWriter<>();
		writer.setEntityManagerFactory(entityManager.getEntityManagerFactory());

		return writer;
	}

	private ItemReader<UUID> messageDestinationIdReader()
	{
		try
		{
			JdbcPagingItemReader<UUID> reader = new JdbcPagingItemReader<>();
			final SqlPagingQueryProviderFactoryBean sqlPagingQueryProviderFactoryBean = new SqlPagingQueryProviderFactoryBean();
			sqlPagingQueryProviderFactoryBean.setDataSource(dataSource);
			sqlPagingQueryProviderFactoryBean.setSelectClause("select id");
			sqlPagingQueryProviderFactoryBean.setFromClause("from message_destinations");
			// sqlPagingQueryProviderFactoryBean.setWhereClause("where 1=1");
			sqlPagingQueryProviderFactoryBean.setSortKey("id");
			reader.setQueryProvider(sqlPagingQueryProviderFactoryBean.getObject());
			reader.setDataSource(dataSource);
			reader.setPageSize(MESSAGE_DESTINATIONS_CHUNK_SIZE);
			reader.setRowMapper(SingleColumnRowMapper.newInstance(UUID.class));
			// reader.setParameterValues(Collections.singletonMap("x", y));
			reader.afterPropertiesSet();
			reader.setSaveState(true);
			logger.info("Reading message destinations in chunks of {}", MESSAGE_DESTINATIONS_CHUNK_SIZE);
			return reader;
		}
		catch (Exception e)
		{
			throw YonaException.unexpected(e);
		}
	}
}