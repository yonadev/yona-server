/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.proxy.HibernateProxy;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "GOALS")
public abstract class Goal extends EntityWithID
{
	public static GoalRepository getRepository()
	{
		return (GoalRepository) RepositoryProvider.getRepository(Goal.class, UUID.class);
	}

	@ManyToOne
	private ActivityCategory activityCategory;

	private ZonedDateTime creationTime;
	private ZonedDateTime endTime;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Goal previousInstanceOfThisGoal;

	// Default constructor is required for JPA
	public Goal()
	{
		super(null);
	}

	protected Goal(UUID id, ZonedDateTime creationTime, ActivityCategory activityCategory)
	{
		super(id);

		if (activityCategory == null)
		{
			throw new IllegalArgumentException("activityCategory cannot be null");
		}
		this.creationTime = creationTime;
		this.activityCategory = activityCategory;
	}

	protected Goal(UUID id, Goal originalGoal, ZonedDateTime endTime)
	{
		super(id);

		if (originalGoal == null)
		{
			throw new IllegalArgumentException("originalGoal cannot be null");
		}
		this.activityCategory = originalGoal.activityCategory;
		this.creationTime = originalGoal.creationTime;
		this.endTime = endTime;
	}

	public ActivityCategory getActivityCategory()
	{
		return activityCategory;
	}

	public ZonedDateTime getCreationTime()
	{
		return creationTime;
	}

	/**
	 * For test purposes only.
	 */
	public void setCreationTime(ZonedDateTime creationTime)
	{
		this.creationTime = creationTime;
	}

	public ZonedDateTime getEndTime()
	{
		return endTime;
	}

	public Optional<Goal> getPreviousVersionOfThisGoal()
	{
		if (previousInstanceOfThisGoal == null)
		{
			return Optional.empty();
		}
		if (previousInstanceOfThisGoal instanceof HibernateProxy)
		{
			// See http://stackoverflow.com/a/2216603/4353482
			// Simply returning the value causes a failure in instanceof and cast
			return Optional
					.of((Goal) ((HibernateProxy) previousInstanceOfThisGoal).getHibernateLazyInitializer().getImplementation());
		}
		return Optional.of(previousInstanceOfThisGoal);
	}

	public void setPreviousVersionOfThisGoal(Goal previousGoal)
	{
		this.previousInstanceOfThisGoal = previousGoal;
	}

	public abstract Goal cloneAsHistoryItem(ZonedDateTime endTime);

	public abstract boolean isMandatory();

	public abstract boolean isNoGoGoal();

	public abstract boolean isGoalAccomplished(DayActivity dayActivity);

	public abstract int computeTotalMinutesBeyondGoal(DayActivity dayActivity);

	public boolean wasActiveAtInterval(ZonedDateTime dateAtStartOfInterval, TemporalUnit timeUnit)
	{
		return wasActiveAtInterval(creationTime, Optional.ofNullable(endTime), dateAtStartOfInterval, timeUnit);
	}

	public static boolean wasActiveAtInterval(ZonedDateTime creationTime, Optional<ZonedDateTime> endTime,
			ZonedDateTime dateAtStartOfInterval, TemporalUnit timeUnit)
	{
		ZonedDateTime startNextInterval = dateAtStartOfInterval.plus(1, timeUnit);
		return creationTime.isBefore(startNextInterval) && endTime.map(end -> end.isAfter(startNextInterval)).orElse(true);
	}

	public Set<UUID> getIDsIncludingHistoryItems()
	{
		Set<UUID> ids = new HashSet<>();
		Optional<Goal> previous = Optional.of(this);
		while (previous.isPresent())
		{
			ids.add(previous.get().getID());
			previous = previous.get().getPreviousVersionOfThisGoal();
		}

		return ids;
	}
}
