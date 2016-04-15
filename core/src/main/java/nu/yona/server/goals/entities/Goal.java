/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
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

	// Default constructor is required for JPA
	public Goal()
	{
		super(null);
	}

	protected Goal(UUID id, ActivityCategory activityCategory)
	{
		super(id);

		if (activityCategory == null)
		{
			throw new IllegalArgumentException("activityCategory cannot be null");
		}
		this.activityCategory = activityCategory;
		this.creationTime = ZonedDateTime.now();
	}

	public ActivityCategory getActivityCategory()
	{
		return activityCategory;
	}

	public ZonedDateTime getCreationTime()
	{
		return creationTime;
	}

	public abstract boolean isMandatory();

	public abstract boolean isNoGoGoal();

	public abstract boolean isGoalAccomplished(DayActivity dayActivity);

	public abstract int computeTotalMinutesBeyondGoal(DayActivity dayActivity);
}
