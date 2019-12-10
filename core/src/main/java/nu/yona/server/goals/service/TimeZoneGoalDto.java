/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.util.TimeUtil;

@JsonRootName("timeZoneGoal")
public class TimeZoneGoalDto extends GoalDto
{
	private static final long serialVersionUID = 7479427103494945857L;

	private static Pattern zonePattern = Pattern.compile("[0-2][0-9]:[0-5][0-9]-[0-2][0-9]:[0-5][0-9]");
	private final List<String> zones;
	private final List<Integer> spreadCells;

	@JsonCreator
	public TimeZoneGoalDto(
			@JsonFormat(pattern = Constants.ISO_DATE_TIME_PATTERN) @JsonProperty("creationTime") Optional<ZonedDateTime> creationTime,
			@JsonProperty(required = true, value = "zones") List<String> zones)
	{
		super(creationTime.map(TimeUtil::toUtcLocalDateTime));

		this.zones = zones;
		this.spreadCells = Collections.emptyList();
	}

	private TimeZoneGoalDto(UUID id, UUID activityCategoryId, List<String> zones, LocalDateTime creationTime,
			Optional<LocalDateTime> endTime, List<Integer> spreadCells)
	{
		super(id, Optional.of(creationTime), endTime, activityCategoryId, false);

		this.zones = zones;
		this.spreadCells = spreadCells;
	}

	@Override
	public String getType()
	{
		return "TimeZoneGoal";
	}

	@Override
	public void validate()
	{
		if ((zones == null) || zones.isEmpty())
		{
			throw GoalServiceException.timeZoneGoalAtLeastOneZoneRequired();
		}
		for (String zone : zones)
		{
			assertValidZone(zone);
		}
	}

	private void assertValidZone(String zone)
	{
		if (!zonePattern.matcher(zone).matches())
		{
			throw GoalServiceException.timeZoneGoalInvalidZoneFormat(zone);
		}
		int[] numbers = Arrays.asList(zone.split("[-:]")).stream().mapToInt(Integer::parseInt).toArray();
		int fromHour = numbers[0];
		int fromMinute = numbers[1];
		int toHour = numbers[2];
		int toMinute = numbers[3];

		assertValidHour(zone, fromHour);
		assertValidMinute(zone, fromMinute);
		assertValidHour(zone, toHour);
		assertValidMinute(zone, toMinute);
		assertToBeyondFrom(zone, fromHour, fromMinute, toHour, toMinute);
		assertNotBeyondTwentyFour(zone, fromHour, fromMinute);
		assertNotBeyondTwentyFour(zone, toHour, toMinute);
	}

	private void assertNotBeyondTwentyFour(String zone, int fromHour, int fromMinute)
	{
		if (fromHour == 24 && fromMinute != 0)
		{
			throw GoalServiceException.timeZoneGoalBeyondTwentyFour(zone, fromMinute);
		}
	}

	private void assertToBeyondFrom(String zone, int fromHour, int fromMinute, int toHour, int toMinute)
	{
		if (fromHour * 60 + fromMinute >= toHour * 60 + toMinute)
		{
			throw GoalServiceException.timeZoneGoalToNotBeyondFrom(zone);
		}
	}

	private void assertValidHour(String zone, int hour)
	{
		if (hour > 24)
		{
			throw GoalServiceException.timeZoneGoalInvalidHour(zone, hour);
		}
	}

	private void assertValidMinute(String zone, int minute)
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

	@Override
	public boolean isNoGoGoal()
	{
		return false;
	}

	public List<String> getZones()
	{
		return zones;
	}

	public List<Integer> getSpreadCells()
	{
		return spreadCells;
	}

	static TimeZoneGoalDto createInstance(TimeZoneGoal entity)
	{
		return new TimeZoneGoalDto(entity.getId(), entity.getActivityCategory().getId(), entity.getZones(),
				entity.getCreationTime(), Optional.ofNullable(entity.getEndTime()), entity.getSpreadCells());
	}

	@Override
	public TimeZoneGoal createGoalEntity()
	{
		ActivityCategory activityCategory = ActivityCategory.getRepository().findById(this.getActivityCategoryId())
				.orElseThrow(() -> ActivityCategoryException.notFound(this.getActivityCategoryId()));
		return TimeZoneGoal.createInstance(getCreationTime().orElse(TimeUtil.utcNow()), activityCategory, this.zones);
	}
}
