/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.entities.ZoneIdAttributeConverter;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.IGoal;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "INTERVAL_ACTIVITIES", uniqueConstraints = @UniqueConstraint(name = "no_duplicate_activities", columnNames = {
		"dtype", "user_anonymized_id", "startDate", "goal_id" }))
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class IntervalActivity extends EntityWithId
{
	private static final Logger logger = LoggerFactory.getLogger(IntervalActivity.class);

	public static final int SPREAD_COUNT = 96;

	@ManyToOne(fetch = FetchType.LAZY)
	private UserAnonymized userAnonymized;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "goal_id")
	private Goal goal;

	@Convert(converter = ZoneIdAttributeConverter.class)
	private ZoneId timeZone;

	/*
	 * The start date in the specified zone
	 */
	private LocalDate startDate;

	@Column(length = SPREAD_COUNT)
	private byte[] spread;

	private int totalActivityDurationMinutes;

	private boolean aggregatesComputed;

	// Default constructor for JPA
	protected IntervalActivity()
	{
	}

	protected IntervalActivity(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startDate)
	{
		this.userAnonymized = Objects.requireNonNull(userAnonymized);
		this.goal = Objects.requireNonNull(goal);
		this.timeZone = Objects.requireNonNull(timeZone);
		this.startDate = Objects.requireNonNull(startDate);
	}

	public static IntervalActivityRepository getIntervalActivityRepository()
	{
		return (IntervalActivityRepository) RepositoryProvider.getRepository(IntervalActivity.class, Long.class);
	}

	protected abstract TemporalUnit getTimeUnit();

	protected abstract List<Integer> computeSpread();

	protected abstract int computeTotalActivityDurationMinutes();

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public void setGoal(Goal goal)
	{
		this.goal = goal;
	}

	public UUID getGoalId()
	{
		return EntityWithUuid.getIdWithoutLoadingEntity(goal);
	}

	public ZoneId getTimeZone()
	{
		return timeZone;
	}

	public LocalDate getStartDate()
	{
		return startDate;
	}

	public ZonedDateTime getStartTime()
	{
		return startDate.atStartOfDay().atZone(timeZone);
	}

	public abstract ZonedDateTime getEndTime();

	public boolean hasPrevious(LocalDate earliestPossibleDate, IGoal goal)
	{
		return hasPrevious(earliestPossibleDate, goal, getStartTime(), getTimeUnit());
	}

	public boolean hasNext()
	{
		return hasNext(getStartTime(), getTimeUnit());
	}

	public static boolean hasPrevious(LocalDate earliestPossibleDate, IGoal goal, ZonedDateTime startTime, TemporalUnit timeUnit)
	{
		ZonedDateTime previousIntervalStart = startTime.minus(1, timeUnit);
		LocalDate previousIntervalStartDate = previousIntervalStart.toLocalDate();
		if (timeUnit == ChronoUnit.WEEKS)
		{
			earliestPossibleDate = TimeUtil.getStartOfWeek(earliestPossibleDate);
		}
		if (previousIntervalStartDate.isBefore(earliestPossibleDate))
		{
			return false;
		}
		return goal.wasActiveAtInterval(previousIntervalStart, timeUnit);
	}

	public static boolean hasNext(ZonedDateTime startTime, TemporalUnit timeUnit)
	{
		return startTime.plus(1, timeUnit).isBefore(ZonedDateTime.now());
	}

	public boolean areAggregatesComputed()
	{
		return aggregatesComputed;
	}

	public void computeAggregates()
	{
		spread = spreadIntegersAsByteArray(computeSpread());
		totalActivityDurationMinutes = computeTotalActivityDurationMinutes();
		aggregatesComputed = true;
	}

	protected void resetAggregatesComputed()
	{
		if (aggregatesComputed)
		{
			logger.info("Resetting aggregates computed on {} interval activity with id {} and start date {}", this.getTimeUnit(),
					this.getId(), this.startDate);
			aggregatesComputed = false;
		}
	}

	public List<Integer> getSpread()
	{
		if (areAggregatesComputed())
		{
			return Collections.unmodifiableList(spreadBytesAsIntegerList());
		}

		return computeSpread();
	}

	public int getTotalActivityDurationMinutes()
	{
		if (areAggregatesComputed())
		{
			return totalActivityDurationMinutes;
		}

		return computeTotalActivityDurationMinutes();
	}

	private List<Integer> spreadBytesAsIntegerList()
	{
		assert spread.length == SPREAD_COUNT;

		List<Integer> integers = new ArrayList<>(spread.length);
		for (int i = 0; (i < spread.length); i++)
		{
			integers.add((int) spread[i]);
		}
		return integers;
	}

	private static byte[] spreadIntegersAsByteArray(List<Integer> spread)
	{
		byte[] bytes = new byte[spread.size()];
		for (int i = 0; i < spread.size(); i++)
		{
			bytes[i] = (byte) spread.get(i).intValue();
		}
		return bytes;
	}

	protected static List<Integer> getEmptySpread()
	{
		return new ArrayList<>(Collections.nCopies(96, 0));
	}
}
