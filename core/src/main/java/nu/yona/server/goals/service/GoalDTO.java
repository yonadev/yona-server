/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

import nu.yona.server.Constants;
import nu.yona.server.entities.EntityUtil;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.rest.PolymorphicDTO;

@JsonRootName("goal")
@JsonSubTypes({ @Type(value = BudgetGoalDTO.class, name = "BudgetGoal"),
		@Type(value = TimeZoneGoalDTO.class, name = "TimeZoneGoal") })
public abstract class GoalDTO extends PolymorphicDTO implements Serializable
{
	private static final long serialVersionUID = 2825849099414812967L;

	private final UUID id;
	private UUID activityCategoryID;
	private final ZonedDateTime creationTime;
	private final boolean mandatory;
	private final ZonedDateTime endTime;

	protected GoalDTO(UUID id, Optional<ZonedDateTime> creationTime, Optional<ZonedDateTime> endTime, UUID activityCategoryID,
			boolean mandatory)
	{
		Objects.requireNonNull(creationTime);
		this.id = id;
		this.setActivityCategoryID(activityCategoryID);
		this.mandatory = mandatory;

		// not using Optional as field here because of implementing Serializable
		// see
		// http://stackoverflow.com/questions/24547673/why-java-util-optional-is-not-serializable-how-to-serialize-the-object-with-suc
		this.creationTime = creationTime.orElse(null);
		this.endTime = endTime.orElse(null);
	}

	protected GoalDTO(Optional<ZonedDateTime> creationTime)
	{
		this(null, creationTime, Optional.empty(), null, false /* ignored */);
	}

	public abstract void validate();

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public Optional<ZonedDateTime> getCreationTime()
	{
		return Optional.ofNullable(creationTime);
	}

	public boolean isHistoryItem()
	{
		return endTime != null;
	}

	public boolean wasActiveAtInterval(ZonedDateTime dateAtStartOfInterval, TemporalUnit timeUnit)
	{
		if (creationTime == null)
		{
			return false;
		}
		return Goal.wasActiveAtInterval(creationTime, Optional.ofNullable(endTime), dateAtStartOfInterval, timeUnit);
	}

	@JsonIgnore
	public UUID getActivityCategoryID()
	{
		return activityCategoryID;
	}

	@JsonIgnore
	public void setActivityCategoryID(UUID activityCategoryID)
	{
		this.activityCategoryID = activityCategoryID;
	}

	@JsonIgnore
	public boolean isMandatory()
	{
		return mandatory;
	}

	public abstract Goal createGoalEntity();

	public abstract boolean isGoalChanged(Goal existingGoal);

	public abstract void updateGoalEntity(Goal existingGoal);

	@JsonIgnore
	public abstract boolean isNoGoGoal();

	public static GoalDTO createInstance(Goal goal)
	{
		goal = EntityUtil.enforceLoading(goal);
		if (goal instanceof BudgetGoal)
		{
			return BudgetGoalDTO.createInstance((BudgetGoal) goal);
		}
		else if (goal instanceof TimeZoneGoal)
		{
			return TimeZoneGoalDTO.createInstance((TimeZoneGoal) goal);
		}

		throw new NotImplementedException("GoalDTO creation not implemented yet for class " + goal.getClass());
	}
}
