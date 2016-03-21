/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.TimeZoneGoal;

@JsonRootName("timeZoneGoal")
public class TimeZoneGoalDTO extends GoalDTO
{
	private final String[] zones;

	@JsonCreator
	public TimeZoneGoalDTO(@JsonProperty("activityCategoryName") String activityCategoryName,
			@JsonProperty("zones") String[] zones)
	{
		this(null, activityCategoryName, zones);
	}

	private TimeZoneGoalDTO(UUID id, String activityCategoryName, String[] zones)
	{
		super(id, activityCategoryName, false);

		this.zones = zones;
	}

	@Override
	public String getType()
	{
		return "TimeZoneGoal";
	}

	public String[] getZones()
	{
		return zones;
	}

	static TimeZoneGoalDTO createInstance(TimeZoneGoal entity)
	{
		return new TimeZoneGoalDTO(entity.getID(), entity.getActivityCategory().getName(), entity.getZones());
	}

	public TimeZoneGoal createGoalEntity()
	{
		ActivityCategory activityCategory = ActivityCategory.getRepository().findByName(this.getActivityCategoryName());
		if (activityCategory == null)
		{
			throw ActivityCategoryNotFoundException.notFoundByName(this.getActivityCategoryName());
		}
		return TimeZoneGoal.createInstance(activityCategory, this.zones);
	}
}
