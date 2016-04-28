/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;

@JsonRootName("timeZoneGoal")
public class TimeZoneGoalDTO extends GoalDTO
{
	private static Pattern zonePattern = Pattern.compile("[0-2][0-9]:[0-5][0-9]-[0-2][0-9]:[0-5][0-9]");
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
	public void validate()
	{
		if ((zones == null) || zones.length == 0)
		{
			throw GoalServiceException.timeZoneGoalAtLeastOneZoneRequired();
		}
		for (String zone : zones)
		{
			validateZone(zone);
		}
	}

	private void validateZone(String zone)
	{
		if (!zonePattern.matcher(zone).matches())
		{
			throw GoalServiceException.timeZoneGoalInvalidZoneFormat(zone);
		}
		int[] numbers = Arrays.asList(zone.split("[-:]")).stream().mapToInt(Integer::parseInt).toArray();
		validateHour(zone, numbers[0]);
		validateMinute(zone, numbers[1]);
		validateHour(zone, numbers[2]);
		validateMinute(zone, numbers[3]);
		if (numbers[0] * 60 + numbers[1] >= numbers[2] * 60 + numbers[3])
		{
			throw GoalServiceException.timeZoneGoalToNotBeyondFrom(zone);
		}
	}

	private void validateHour(String zone, int hour)
	{
		if (hour > 23)
		{
			throw GoalServiceException.timeZoneGoalInvalidHour(zone, hour);
		}
	}

	private void validateMinute(String zone, int minute)
	{
		if (minute % 15 != 0)
		{
			throw GoalServiceException.timeZoneGoalNotQuarterHour(zone, minute);
		}
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
