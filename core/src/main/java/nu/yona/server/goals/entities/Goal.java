/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

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

	public Goal getPreviousVersionOfThisGoal()
	{
		return previousInstanceOfThisGoal;
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

	public boolean wasActiveAtInterval(ZonedDateTime dateAtStartOfInterval, ChronoUnit timeUnit)
	{
		return creationTime.isBefore(dateAtStartOfInterval.plus(1, timeUnit));
	}
}
