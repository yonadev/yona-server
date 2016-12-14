/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.LocalDateTime;
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

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.entities.EntityUtil;
import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.util.TimeUtil;

@Entity
@Table(name = "GOALS")
public abstract class Goal extends EntityWithId
{
	public static GoalRepository getRepository()
	{
		return (GoalRepository) RepositoryProvider.getRepository(Goal.class, UUID.class);
	}

	@ManyToOne
	private ActivityCategory activityCategory;

	private LocalDateTime creationTime;
	private LocalDateTime endTime;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Goal previousInstanceOfThisGoal;

	// Default constructor is required for JPA
	public Goal()
	{
		super(null);
	}

	protected Goal(UUID id, LocalDateTime creationTime, ActivityCategory activityCategory)
	{
		super(id);

		if (activityCategory == null)
		{
			throw new IllegalArgumentException("activityCategory cannot be null");
		}
		this.creationTime = creationTime;
		this.activityCategory = activityCategory;
	}

	protected Goal(UUID id, Goal originalGoal, LocalDateTime endTime)
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

	public LocalDateTime getCreationTime()
	{
		return creationTime;
	}

	/**
	 * For test purposes only.
	 */
	public void setCreationTime(LocalDateTime creationTime)
	{
		this.creationTime = creationTime;
	}

	public LocalDateTime getEndTime()
	{
		return endTime;
	}

	public Optional<Goal> getPreviousVersionOfThisGoal()
	{
		if (previousInstanceOfThisGoal == null)
		{
			return Optional.empty();
		}
		return Optional.of(EntityUtil.enforceLoading(previousInstanceOfThisGoal));
	}

	public void setPreviousVersionOfThisGoal(Goal previousGoal)
	{
		this.previousInstanceOfThisGoal = previousGoal;
	}

	public abstract Goal cloneAsHistoryItem(LocalDateTime endTime);

	public abstract boolean isMandatory();

	public abstract boolean isNoGoGoal();

	public abstract boolean isGoalAccomplished(DayActivity dayActivity);

	public abstract int computeTotalMinutesBeyondGoal(DayActivity dayActivity);

	public boolean wasActiveAtInterval(ZonedDateTime dateAtStartOfInterval, TemporalUnit timeUnit)
	{
		return wasActiveAtInterval(creationTime, Optional.ofNullable(endTime), dateAtStartOfInterval, timeUnit);
	}

	public static boolean wasActiveAtInterval(LocalDateTime creationTime, Optional<LocalDateTime> endTime,
			ZonedDateTime dateAtStartOfInterval, TemporalUnit timeUnit)
	{
		LocalDateTime startNextInterval = TimeUtil.toUtcLocalDateTime(dateAtStartOfInterval.plus(1, timeUnit));
		return creationTime.isBefore(startNextInterval) && endTime.map(end -> end.isAfter(startNextInterval)).orElse(true);
	}

	public Set<UUID> getIdsIncludingHistoryItems()
	{
		Set<UUID> ids = new HashSet<>();
		Optional<Goal> previous = Optional.of(this);
		while (previous.isPresent())
		{
			ids.add(previous.get().getId());
			previous = previous.get().getPreviousVersionOfThisGoal();
		}

		return ids;
	}
}
