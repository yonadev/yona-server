/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.rest.PolymorphicDTO;

@JsonRootName("goal")
@JsonSubTypes({ @Type(value = BudgetGoalDTO.class, name = "BudgetGoal"),
		@Type(value = TimeZoneGoalDTO.class, name = "TimeZoneGoal") })
public abstract class GoalDTO extends PolymorphicDTO
{
	private final UUID id;
	private UUID activityCategoryID;
	private final ZonedDateTime creationTime;
	private final boolean mandatory;

	protected GoalDTO(UUID id, UUID activityCategoryID, ZonedDateTime creationTime, boolean mandatory)
	{
		this.id = id;
		this.setActivityCategoryID(activityCategoryID);
		this.creationTime = creationTime;
		this.mandatory = mandatory;
	}

	public GoalDTO(String activityCategoryUrl)
	{
		this(null, null, null /* ignored */, false /* ignored */);
	}

	public abstract void validate();

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
	public ZonedDateTime getCreationTime()
	{
		return creationTime;
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

	public static GoalDTO createInstance(Goal goal)
	{
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
