/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonRootName;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import nu.yona.server.subscriptions.service.UserAnonymizedService;

@Service
public class HibernateStatisticsService
{
	private static final Logger logger = LoggerFactory.getLogger(HibernateStatisticsService.class);

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	private SessionFactory sessionFactory;

	private LogAppender logAppender;

	@PostConstruct
	public void initialize()
	{
		sessionFactory = getPrimaryEntityManagerFactory();
		logAppender = LogAppender.initialize();
	}

	public void setEnabled(boolean enabled)
	{
		logger.info("HibernateStatisticsService setEnabled({})", enabled);
		getHibernateStatistics().ifPresent(s -> s.setStatisticsEnabled(enabled));
		if (enabled)
		{
			logAppender.start();
		}
		else
		{
			logAppender.stop();
		}
	}

	public boolean isStatisticsEnabled()
	{
		Optional<Statistics> stats = getHibernateStatistics();
		return stats.map(Statistics::isStatisticsEnabled).orElse(false);
	}

	public StatisticsDto getStatistics()
	{
		logger.info("HibernateStatisticsService getStatistics()");
		if (!isStatisticsEnabled())
		{
			throw new IllegalStateException("Hibernate statistics not enabled");
		}

		return statisticsToDto(getHibernateStatistics().get(), logAppender.getMessages());
	}

	public void resetStatistics()
	{
		logger.info("HibernateStatisticsService resetStatistics()");
		if (!isStatisticsEnabled())
		{
			throw new IllegalStateException("Hibernate statistics not enabled");
		}

		resetHibernateStatistics();
		logAppender.clear();
	}

	public void clearAllUserDataCaches()
	{
		logger.info("HibernateStatisticsService clearAllUserDataCaches()");
		userAnonymizedService.clearCache();
		sessionFactory.getCache().evictAllRegions();
	}

	private SessionFactory getPrimaryEntityManagerFactory()
	{
		try
		{
			return this.applicationContext.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class);
		}
		catch (NoSuchBeanDefinitionException e)
		{
			logger.error("Cannot get entity manager bean", e);
			return null;
		}
	}

	private Optional<Statistics> getHibernateStatistics()
	{
		try
		{
			return Optional.ofNullable(sessionFactory.getStatistics());
		}
		catch (PersistenceException e)
		{
			logger.error("Cannot get statistics", e);
			return Optional.empty();
		}
	}

	private StatisticsDto statisticsToDto(Statistics statistics, List<String> sqlStatements)
	{
		return new StatisticsDto(statistics, sqlStatements);
	}

	private void resetHibernateStatistics()
	{
		getHibernateStatistics().ifPresent(Statistics::clear);
	}

	@JsonRootName("statistics")
	public class StatisticsDto
	{
		private final long closeStatementCount;
		private final long collectionFetchCount;
		private final long collectionLoadCount;
		private final long collectionRecreateCount;
		private final long collectionRemoveCount;
		private final long collectionUpdateCount;
		private final long connectCount;
		private final long entityDeleteCount;
		private final long entityFetchCount;
		private final long entityInsertCount;
		private final long entityLoadCount;
		private final long entityUpdateCount;
		private final long flushCount;
		private final long optimisticFailureCount;
		private final long prepareStatementCount;
		private final long queryCacheHitCount;
		private final long queryCacheMissCount;
		private final long queryCachePutCount;
		private final long queryExecutionCount;
		private final long queryExecutionMaxTime;
		private final long secondLevelCacheHitCount;
		private final long secondLevelCacheMissCount;
		private final long secondLevelCachePutCount;
		private final long sessionCloseCount;
		private final long sessionOpenCount;
		private final Instant startTime;
		private final long successfulTransactionCount;
		private final long transactionCount;
		private final List<String> sqlStatements;

		public StatisticsDto(Statistics statistics, List<String> sqlStatements)
		{
			this.closeStatementCount = statistics.getCloseStatementCount();
			this.collectionFetchCount = statistics.getCollectionFetchCount();
			this.collectionLoadCount = statistics.getCollectionLoadCount();
			this.collectionRecreateCount = statistics.getCollectionRecreateCount();
			this.collectionRemoveCount = statistics.getCollectionRemoveCount();
			this.collectionUpdateCount = statistics.getCollectionUpdateCount();
			this.connectCount = statistics.getConnectCount();
			this.entityDeleteCount = statistics.getEntityDeleteCount();
			this.entityFetchCount = statistics.getEntityFetchCount();
			this.entityInsertCount = statistics.getEntityInsertCount();
			this.entityLoadCount = statistics.getEntityLoadCount();
			this.entityUpdateCount = statistics.getEntityUpdateCount();
			this.flushCount = statistics.getFlushCount();
			this.optimisticFailureCount = statistics.getOptimisticFailureCount();
			this.prepareStatementCount = statistics.getPrepareStatementCount();
			this.queryCacheHitCount = statistics.getQueryCacheHitCount();
			this.queryCacheMissCount = statistics.getQueryCacheMissCount();
			this.queryCachePutCount = statistics.getQueryCachePutCount();
			this.queryExecutionCount = statistics.getQueryExecutionCount();
			this.queryExecutionMaxTime = statistics.getQueryExecutionMaxTime();
			this.secondLevelCacheHitCount = statistics.getSecondLevelCacheHitCount();
			this.secondLevelCacheMissCount = statistics.getSecondLevelCacheMissCount();
			this.secondLevelCachePutCount = statistics.getSecondLevelCachePutCount();
			this.sessionCloseCount = statistics.getSessionCloseCount();
			this.sessionOpenCount = statistics.getSessionOpenCount();
			this.startTime = statistics.getStart();
			this.successfulTransactionCount = statistics.getSuccessfulTransactionCount();
			this.transactionCount = statistics.getTransactionCount();
			this.sqlStatements = sqlStatements;
		}

		public long getCloseStatementCount()
		{
			return closeStatementCount;
		}

		public long getCollectionFetchCount()
		{
			return collectionFetchCount;
		}

		public long getCollectionLoadCount()
		{
			return collectionLoadCount;
		}

		public long getCollectionRecreateCount()
		{
			return collectionRecreateCount;
		}

		public long getCollectionRemoveCount()
		{
			return collectionRemoveCount;
		}

		public long getCollectionUpdateCount()
		{
			return collectionUpdateCount;
		}

		public long getConnectCount()
		{
			return connectCount;
		}

		public long getEntityDeleteCount()
		{
			return entityDeleteCount;
		}

		public long getEntityFetchCount()
		{
			return entityFetchCount;
		}

		public long getEntityInsertCount()
		{
			return entityInsertCount;
		}

		public long getEntityLoadCount()
		{
			return entityLoadCount;
		}

		public long getEntityUpdateCount()
		{
			return entityUpdateCount;
		}

		public long getFlushCount()
		{
			return flushCount;
		}

		public long getOptimisticFailureCount()
		{
			return optimisticFailureCount;
		}

		public long getPrepareStatementCount()
		{
			return prepareStatementCount;
		}

		public long getQueryCacheHitCount()
		{
			return queryCacheHitCount;
		}

		public long getQueryCacheMissCount()
		{
			return queryCacheMissCount;
		}

		public long getQueryCachePutCount()
		{
			return queryCachePutCount;
		}

		public long getQueryExecutionCount()
		{
			return queryExecutionCount;
		}

		public long getQueryExecutionMaxTime()
		{
			return queryExecutionMaxTime;
		}

		public long getSecondLevelCacheHitCount()
		{
			return secondLevelCacheHitCount;
		}

		public long getSecondLevelCacheMissCount()
		{
			return secondLevelCacheMissCount;
		}

		public long getSecondLevelCachePutCount()
		{
			return secondLevelCachePutCount;
		}

		public long getSessionCloseCount()
		{
			return sessionCloseCount;
		}

		public long getSessionOpenCount()
		{
			return sessionOpenCount;
		}

		public Instant getStartTime()
		{
			return startTime;
		}

		public long getSuccessfulTransactionCount()
		{
			return successfulTransactionCount;
		}

		public long getTransactionCount()
		{
			return transactionCount;
		}

		public List<String> getSqlStatements()
		{
			return sqlStatements;
		}
	}

	private static class LogAppender extends AppenderBase<ILoggingEvent>
	{
		private final List<String> messages = new ArrayList<>();
		private final ch.qos.logback.classic.Logger sqlStatementLogger;
		private Level originalLevel;

		LogAppender(ch.qos.logback.classic.Logger sqlStatementLogger)
		{
			this.sqlStatementLogger = sqlStatementLogger;
		}

		static LogAppender initialize()
		{
			ch.qos.logback.classic.Logger sqlStatementLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
					"org.hibernate.SQL");
			LogAppender logAppender = new LogAppender(sqlStatementLogger);
			sqlStatementLogger.addAppender(logAppender);
			return logAppender;
		}

		@Override
		protected void append(ILoggingEvent eventObject)
		{
			if (eventObject.getLevel() != Level.DEBUG)
			{
				return;
			}
			messages.add(eventObject.getFormattedMessage());
		}

		@Override
		public void start()
		{
			if (isStarted())
			{
				return;
			}
			originalLevel = sqlStatementLogger.getLevel();
			sqlStatementLogger.setLevel(Level.DEBUG);
			super.start();
		}

		@Override
		public void stop()
		{
			if (!isStarted())
			{
				return;
			}
			sqlStatementLogger.setLevel(originalLevel);
			super.stop();
		}

		List<String> getMessages()
		{
			return messages;
		}

		void clear()
		{
			messages.clear();
		}
	}
}
