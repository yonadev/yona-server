/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.LocalDateAttributeConverter;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "INTERVAL_ACTIVITIES", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "DTYPE", "USER_ANONYMIZED_ID", "DATE", "GOAL_ID" }) })
public abstract class IntervalActivity extends EntityWithID
{
	public static IntervalActivityRepository getIntervalActivityRepository()
	{
		return (IntervalActivityRepository) RepositoryProvider.getRepository(IntervalActivity.class, UUID.class);
	}

	public static final int SPREAD_COUNT = 96;

	@ManyToOne
	private UserAnonymized userAnonymized;

	@ManyToOne
	private Goal goal;

	/*
	 * The date. Stored for easier querying (if the time zone of the user changes, we cannot query for equal start time).
	 */
	@Convert(converter = LocalDateAttributeConverter.class)
	private LocalDate date;

	/*
	 * The start date and time with saved time zone.
	 */
	private ZonedDateTime startTime;

	@ElementCollection
	private List<Integer> spread;

	private int totalActivityDurationMinutes;

	private boolean aggregatesComputed;

	// Default constructor for JPA
	protected IntervalActivity()
	{
		super(null);
	}

	protected IntervalActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startTime, List<Integer> spread,
			int totalActivityDurationMinutes, boolean aggregatesComputed)
	{
		super(id);
		Objects.requireNonNull(userAnonymized);
		Objects.requireNonNull(goal);
		Objects.requireNonNull(startTime);
		Objects.requireNonNull(spread);
		this.userAnonymized = userAnonymized;
		this.goal = goal;
		this.date = startTime.toLocalDate();
		this.startTime = startTime;
		this.spread = spread;
		this.totalActivityDurationMinutes = totalActivityDurationMinutes;
		this.aggregatesComputed = aggregatesComputed;
	}

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

	public LocalDate getDate()
	{
		return date;
	}

	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	public abstract ZonedDateTime getEndTime();

	public boolean hasPrevious()
	{
		return goal.wasActiveAtInterval(startTime.minus(1, getTimeUnit()), getTimeUnit());
	}

	public boolean hasNext()
	{
		return startTime.plus(1, getTimeUnit()).isBefore(ZonedDateTime.now());
	}

	protected abstract TemporalUnit getTimeUnit();

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

	protected abstract List<Integer> computeSpread();

	public int getTotalActivityDurationMinutes()
	{
		if (areAggregatesComputed())
		{
			return totalActivityDurationMinutes;
		}

		return computeTotalActivityDurationMinutes();
	}

	protected abstract int computeTotalActivityDurationMinutes();

	protected static List<Integer> getEmptySpread()
	{
		return new ArrayList<Integer>(Collections.nCopies(96, 0));
	}
}
