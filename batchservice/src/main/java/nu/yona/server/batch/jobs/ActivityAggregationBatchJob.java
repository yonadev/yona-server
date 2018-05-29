/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.jobs;

import java.sql.Date;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;

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

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.TimeUtil;

@Component
public class ActivityAggregationBatchJob
{
	private static final Logger logger = LoggerFactory.getLogger(ActivityAggregationBatchJob.class);

	private static final int DAY_ACTIVITY_CHUNK_SIZE = 200;
	private static final int WEEK_ACTIVITY_CHUNK_SIZE = 200;
	// once we have users in multiple zones, the batch job should become sensitive to user time zone
	private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Amsterdam");

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	@Qualifier("activityAggregationJobDayActivityReader")
	private ItemReader<Long> dayActivityReader;

	@Autowired
	@Qualifier("activityAggregationJobWeekActivityReader")
	private ItemReader<Long> weekActivityReader;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DayActivityRepository dayActivityRepository;

	@Autowired
	private WeekActivityRepository weekActivityRepository;

	@Bean("activityAggregationJob")
	public Job activityAggregationBatchJob()
	{
		return jobBuilderFactory.get("activityAggregationBatchJob").incrementer(new RunIdIncrementer())
				.flow(aggregateDayActivities()).next(aggregateWeekActivities()).end().build();
	}

	private Step aggregateDayActivities()
	{
		return stepBuilderFactory.get("aggregateDayActivities").<Long, DayActivity> chunk(DAY_ACTIVITY_CHUNK_SIZE)
				.reader(dayActivityReader).processor(dayActivityProcessor()).writer(dayActivityWriter()).build();
	}

	private Step aggregateWeekActivities()
	{
		return stepBuilderFactory.get("aggregateWeekActivities").<Long, WeekActivity> chunk(WEEK_ACTIVITY_CHUNK_SIZE)
				.reader(weekActivityReader).processor(weekActivityProcessor()).writer(weekActivityWriter()).build();
	}

	@Bean(name = "activityAggregationJobDayActivityReader", destroyMethod = "")
	@StepScope
	public ItemReader<Long> dayActivityReader()
	{
		return intervalActivityIdReader(Date.valueOf(
				TimeUtil.getStartOfDay(DEFAULT_TIME_ZONE, ZonedDateTime.now(DEFAULT_TIME_ZONE)).minusDays(1).toLocalDate()),
				DayActivity.class, DAY_ACTIVITY_CHUNK_SIZE);
	}

	private ItemProcessor<Long, DayActivity> dayActivityProcessor()
	{
		return new ItemProcessor<Long, DayActivity>() {
			@Override
			public DayActivity process(Long dayActivityId) throws Exception
			{
				logger.debug("Processing day activity with id {}", dayActivityId);
				DayActivity dayActivity = dayActivityRepository.findOne(dayActivityId);

				dayActivity.computeAggregates();
				return dayActivity;
			}
		};
	}

	private JpaItemWriter<DayActivity> dayActivityWriter()
	{
		JpaItemWriter<DayActivity> writer = new JpaItemWriter<>();
		writer.setEntityManagerFactory(entityManager.getEntityManagerFactory());

		return writer;
	}

	@Bean(name = "activityAggregationJobWeekActivityReader", destroyMethod = "")
	@StepScope
	public ItemReader<Long> weekActivityReader()
	{
		return intervalActivityIdReader(Date.valueOf(
				TimeUtil.getStartOfWeek(DEFAULT_TIME_ZONE, ZonedDateTime.now(DEFAULT_TIME_ZONE)).minusWeeks(1).toLocalDate()),
				WeekActivity.class, WEEK_ACTIVITY_CHUNK_SIZE);
	}

	private ItemProcessor<Long, WeekActivity> weekActivityProcessor()
	{
		return new ItemProcessor<Long, WeekActivity>() {
			@Override
			public WeekActivity process(Long weekActivityId) throws Exception
			{
				logger.debug("Processing week activity with id {}", weekActivityId);
				WeekActivity weekActivity = weekActivityRepository.findOne(weekActivityId);

				weekActivity.computeAggregates();
				return weekActivity;
			}
		};
	}

	private JpaItemWriter<WeekActivity> weekActivityWriter()
	{
		JpaItemWriter<WeekActivity> writer = new JpaItemWriter<>();
		writer.setEntityManagerFactory(entityManager.getEntityManagerFactory());

		return writer;
	}

	private ItemReader<Long> intervalActivityIdReader(Date cutOffDate, Class<?> activityClass, int chunkSize)
	{
		SqlPagingQueryProviderFactoryBean queryProviderFactory = createQueryProviderFactory(activityClass);
		JdbcPagingItemReader<Long> reader = createReader(cutOffDate, chunkSize, queryProviderFactory);
		logger.info("Reading nonaggregated {} entities with startDate <= {} in chunks of {}", activityClass.getSimpleName(),
				cutOffDate, chunkSize);
		return reader;
	}

	private SqlPagingQueryProviderFactoryBean createQueryProviderFactory(Class<?> activityClass)
	{
		SqlPagingQueryProviderFactoryBean sqlPagingQueryProviderFactoryBean = new SqlPagingQueryProviderFactoryBean();
		sqlPagingQueryProviderFactoryBean.setDataSource(dataSource);
		sqlPagingQueryProviderFactoryBean.setSelectClause("select id");
		sqlPagingQueryProviderFactoryBean.setFromClause("from interval_activities");
		sqlPagingQueryProviderFactoryBean.setWhereClause("where dtype = '" + activityClass.getSimpleName()
				+ "' and aggregates_computed = 0 and start_date <= :cutOffDate");
		sqlPagingQueryProviderFactoryBean.setSortKey("id");
		return sqlPagingQueryProviderFactoryBean;
	}

	private JdbcPagingItemReader<Long> createReader(Date cutOffDate, int chunkSize,
			SqlPagingQueryProviderFactoryBean sqlPagingQueryProviderFactoryBean)
	{
		try
		{
			JdbcPagingItemReader<Long> reader = new JdbcPagingItemReader<>();
			reader.setQueryProvider(sqlPagingQueryProviderFactoryBean.getObject());
			reader.setDataSource(dataSource);
			reader.setPageSize(chunkSize);
			reader.setRowMapper(SingleColumnRowMapper.newInstance(Long.class));
			reader.setParameterValues(Collections.singletonMap("cutOffDate", cutOffDate));
			reader.afterPropertiesSet();
			reader.setSaveState(true);
			return reader;
		}
		catch (Exception e)
		{
			throw YonaException.unexpected(e);
		}
	}
}