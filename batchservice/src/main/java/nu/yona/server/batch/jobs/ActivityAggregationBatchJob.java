/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.jobs;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.util.TimeUtil;

@Component
public class ActivityAggregationBatchJob
{
	private static final Logger logger = LoggerFactory.getLogger(ActivityAggregationBatchJob.class);

	private static final int DAY_ACTIVITY_CHUNK_SIZE = 10;
	private static final int WEEK_ACTIVITY_CHUNK_SIZE = 10;
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
	private JpaPagingItemReader<DayActivity> dayActivityReader;

	@Autowired
	@Qualifier("activityAggregationJobWeekActivityReader")
	private JpaPagingItemReader<WeekActivity> weekActivityReader;

	@Bean("activityAggregationJob")
	public Job activityAggregationBatchJob()
	{
		return jobBuilderFactory.get("activityAggregationBatchJob").incrementer(new RunIdIncrementer())
				.flow(aggregateDayActivities()).next(aggregateWeekActivities()).end().build();
	}

	private Step aggregateDayActivities()
	{
		return stepBuilderFactory.get("aggregateDayActivities").<DayActivity, DayActivity> chunk(DAY_ACTIVITY_CHUNK_SIZE)
				.reader(dayActivityReader).processor(dayActivityProcessor()).writer(dayActivityWriter()).build();
	}

	private Step aggregateWeekActivities()
	{
		return stepBuilderFactory.get("aggregateWeekActivities").<WeekActivity, WeekActivity> chunk(WEEK_ACTIVITY_CHUNK_SIZE)
				.reader(weekActivityReader).processor(weekActivityProcessor()).writer(weekActivityWriter()).build();
	}

	@Bean(name = "activityAggregationJobDayActivityReader", destroyMethod = "")
	@StepScope
	public JpaPagingItemReader<DayActivity> dayActivityReader()
	{
		try
		{
			String jpqlQuery = "SELECT d FROM DayActivity d WHERE d.startDate <= :yesterday AND d.aggregatesComputed = false";

			JpaPagingItemReader<DayActivity> reader = new JpaPagingItemReader<>();
			reader.setQueryString(jpqlQuery);
			reader.setParameterValues(Collections.singletonMap("yesterday",
					TimeUtil.getStartOfDay(DEFAULT_TIME_ZONE, ZonedDateTime.now(DEFAULT_TIME_ZONE).minusDays(1)).toLocalDate()));
			reader.setEntityManagerFactory(entityManager.getEntityManagerFactory());
			reader.setPageSize(DAY_ACTIVITY_CHUNK_SIZE);
			reader.afterPropertiesSet();
			reader.setSaveState(true);

			return reader;
		}
		catch (Exception e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private ItemProcessor<DayActivity, DayActivity> dayActivityProcessor()
	{
		return new ItemProcessor<DayActivity, DayActivity>() {
			@Override
			public DayActivity process(DayActivity dayActivity) throws Exception
			{
				logger.debug("Processing day activity with id {}, start date {}", dayActivity.getId(),
						dayActivity.getStartDate());

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
	public JpaPagingItemReader<WeekActivity> weekActivityReader()
	{
		try
		{
			String jpqlQuery = "SELECT w FROM WeekActivity w WHERE w.startDate <= :lastWeek AND w.aggregatesComputed = false";

			JpaPagingItemReader<WeekActivity> reader = new JpaPagingItemReader<>();
			reader.setQueryString(jpqlQuery);
			reader.setParameterValues(Collections.singletonMap("lastWeek",
					TimeUtil.getStartOfWeek(ZonedDateTime.now(DEFAULT_TIME_ZONE).minusWeeks(1).toLocalDate())));
			reader.setEntityManagerFactory(entityManager.getEntityManagerFactory());
			reader.setPageSize(WEEK_ACTIVITY_CHUNK_SIZE);
			reader.afterPropertiesSet();
			reader.setSaveState(true);

			return reader;
		}
		catch (Exception e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private ItemProcessor<WeekActivity, WeekActivity> weekActivityProcessor()
	{
		return new ItemProcessor<WeekActivity, WeekActivity>() {
			@Override
			public WeekActivity process(WeekActivity weekActivity) throws Exception
			{
				logger.debug("Processing week activity with id {}, start date {}", weekActivity.getId(),
						weekActivity.getStartDate());

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
}
