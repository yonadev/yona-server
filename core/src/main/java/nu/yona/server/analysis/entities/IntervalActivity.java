/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.EntityUtil;
import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.entities.ZoneIdAttributeConverter;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "INTERVAL_ACTIVITIES")
// We used to have a unique constraint defined here: @UniqueConstraint(columnNames = { "dtype", "user_anonymized", "startDate",
// "goal" })
// Due to an inconsistency between Liquibase and JPA, we have moved this to Liquibase (extra.yml)
public abstract class IntervalActivity extends EntityWithId
{
	public static IntervalActivityRepository getIntervalActivityRepository()
	{
		return (IntervalActivityRepository) RepositoryProvider.getRepository(IntervalActivity.class, Long.class);
	}

	public static final int SPREAD_COUNT = 96;

	@ManyToOne
	private UserAnonymized userAnonymized;

	@ManyToOne
	private Goal goal;

	@Convert(converter = ZoneIdAttributeConverter.class)
	private ZoneId timeZone;

	/*
	 * The start date in the specified zone
	 */
	private LocalDate startDate;

	@ElementCollection
	private List<Integer> spread;

	private int totalActivityDurationMinutes;

	private boolean aggregatesComputed;

	// Default constructor for JPA
	protected IntervalActivity()
	{
		super();
	}

	protected IntervalActivity(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startDate,
			List<Integer> spread, int totalActivityDurationMinutes, boolean aggregatesComputed)
	{
		super();
		Objects.requireNonNull(userAnonymized);
		Objects.requireNonNull(goal);
		Objects.requireNonNull(timeZone);
		Objects.requireNonNull(startDate);
		Objects.requireNonNull(spread);
		this.userAnonymized = userAnonymized;
		this.goal = goal;
		this.timeZone = timeZone;
		this.startDate = startDate;
		this.spread = spread;
		this.totalActivityDurationMinutes = totalActivityDurationMinutes;
		this.aggregatesComputed = aggregatesComputed;
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
		return EntityUtil.enforceLoading(goal);
	}

	public void setGoal(Goal goal)
	{
		this.goal = goal;
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

	public boolean hasPrevious()
	{
		return hasPrevious(goal, getStartTime(), getTimeUnit());
	}

	public boolean hasNext()
	{
		return hasNext(getStartTime(), getTimeUnit());
	}

	public static boolean hasPrevious(Goal goal, ZonedDateTime startTime, TemporalUnit timeUnit)
	{
		return goal.wasActiveAtInterval(startTime.minus(1, timeUnit), timeUnit);
	}

	public static boolean hasNext(ZonedDateTime startTime, TemporalUnit timeUnit)
	{
		return startTime.plus(1, timeUnit).isBefore(ZonedDateTime.now());
	}

	public boolean areAggregatesComputed()
	{
		return aggregatesComputed;
	}

	public List<Integer> getSpread()
	{
		if (areAggregatesComputed())
		{
			return Collections.unmodifiableList(spread);
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

	protected static List<Integer> getEmptySpread()
	{
		return new ArrayList<Integer>(Collections.nCopies(96, 0));
	}
}
