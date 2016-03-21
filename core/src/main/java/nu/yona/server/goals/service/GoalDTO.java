/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

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
	private final String activityCategoryName;
	private final boolean mandatory;

	protected GoalDTO(UUID id, String activityCategoryName, boolean mandatory)
	{
		this.id = id;
		this.activityCategoryName = activityCategoryName;
		this.mandatory = mandatory;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	public String getActivityCategoryName()
	{
		return activityCategoryName;
	}

	@JsonIgnore
	public boolean isMandatory()
	{
		return mandatory;
	}

	public abstract Goal createGoalEntity();

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
