/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;

@JsonRootName("timeZoneGoal")
public class TimeZoneGoalDTO extends GoalDTO
{
	private final String[] zones;

	@JsonCreator
	public TimeZoneGoalDTO(@JsonProperty(required = true, value = "zones") String[] zones)
	{
		super(null);

		this.zones = zones;
	}

	private TimeZoneGoalDTO(UUID id, UUID activityCategoryID, String[] zones, ZonedDateTime creationTime)
	{
		super(id, activityCategoryID, creationTime, false);

		this.zones = zones;
	}

	@Override
	public String getType()
	{
		return "TimeZoneGoal";
	}

	@Override
	public void updateGoalEntity(Goal existingGoal)
	{
		((TimeZoneGoal) existingGoal).setZones(zones);
	}

	public String[] getZones()
	{
		return zones;
	}

	static TimeZoneGoalDTO createInstance(TimeZoneGoal entity)
	{
		return new TimeZoneGoalDTO(entity.getID(), entity.getActivityCategory().getID(), entity.getZones(),
				entity.getCreationTime());
	}

	public TimeZoneGoal createGoalEntity()
	{
		ActivityCategory activityCategory = ActivityCategory.getRepository().findOne(this.getActivityCategoryID());
		if (activityCategory == null)
		{
			throw ActivityCategoryNotFoundException.notFound(this.getActivityCategoryID());
		}
		return TimeZoneGoal.createInstance(activityCategory, this.zones);
	}
}
