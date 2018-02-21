/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

import nu.yona.server.Constants;
import nu.yona.server.entities.EntityUtil;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.rest.PolymorphicDto;
import nu.yona.server.util.TimeUtil;

/**
 * The base goal DTO class.<br/>
 * NOTE: The ID is always available except when a new goal is POSTed. To keep the API simple, the DTO always has an ID. In case of
 * a freshly posted goal, it is an all-zeros UUID.
 */
@JsonRootName("goal")
@JsonSubTypes({ @Type(value = BudgetGoalDto.class, name = "BudgetGoal"),
		@Type(value = TimeZoneGoalDto.class, name = "TimeZoneGoal") })
public abstract class GoalDto extends PolymorphicDto implements Serializable
{
	private static final long serialVersionUID = 2825849099414812967L;

	private final UUID id;
	private UUID activityCategoryId;
	private final LocalDateTime creationTime;
	private final boolean mandatory;
	private final LocalDateTime endTime;

	protected GoalDto(UUID id, Optional<LocalDateTime> creationTime, Optional<LocalDateTime> endTime, UUID activityCategoryId,
			boolean mandatory)
	{
		this.id = (id == null) ? new UUID(0, 0) : id;
		this.setActivityCategoryId(activityCategoryId);
		this.mandatory = mandatory;

		// not using Optional as field here because of implementing Serializable
		// see
		// http://stackoverflow.com/questions/24547673/why-java-util-optional-is-not-serializable-how-to-serialize-the-object-with-suc
		this.creationTime = Objects.requireNonNull(creationTime).orElse(null);
		this.endTime = endTime.orElse(null);
	}

	protected GoalDto(Optional<LocalDateTime> creationTime)
	{
		this(null, creationTime, Optional.empty(), null, false /* ignored */);
	}

	@Override
	public final int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public final boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (!(obj instanceof GoalDto))
		{
			return false;
		}
		GoalDto that = (GoalDto) obj;
		return this.id.equals(that.id);
	}

	@Override
	public String toString()
	{
		return this.getClass().getSimpleName() + " with ID " + id + " for activity category " + activityCategoryId;
	}

	public abstract void validate();

	@JsonIgnore
	public UUID getGoalId()
	{
		return id;
	}

	@JsonProperty("creationTime")
	@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN)
	public Optional<ZonedDateTime> getCreationTimeAsZonedDateTime()
	{
		return Optional.ofNullable(creationTime).map(TimeUtil::toUtcZonedDateTime);
	}

	@JsonIgnore
	public Optional<LocalDateTime> getCreationTime()
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
	public UUID getActivityCategoryId()
	{
		return activityCategoryId;
	}

	@JsonIgnore
	public void setActivityCategoryId(UUID activityCategoryId)
	{
		this.activityCategoryId = activityCategoryId;
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

	public static GoalDto createInstance(Goal goal)
	{
		goal = EntityUtil.enforceLoading(goal);
		if (goal instanceof BudgetGoal)
		{
			return BudgetGoalDto.createInstance((BudgetGoal) goal);
		}
		else if (goal instanceof TimeZoneGoal)
		{
			return TimeZoneGoalDto.createInstance((TimeZoneGoal) goal);
		}

		throw new NotImplementedException("GoalDto creation not implemented yet for class " + goal.getClass());
	}
}
