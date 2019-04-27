/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.entities.EntityUtil;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.TimeZoneGoalDto;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;

@JsonRootName("dayActivity")
public class DayActivityDto extends IntervalActivityDto
{
	private static final DateTimeFormatter ISO8601_DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final boolean goalAccomplished;
	private final int totalMinutesBeyondGoal;

	private final UUID activityCategoryId;

	private DayActivityDto(UUID goalId, UUID activityCategoryId, ZonedDateTime startTime, boolean shouldSerializeDate,
			List<Integer> spread, int totalActivityDurationMinutes, boolean goalAccomplished, int totalMinutesBeyondGoal,
			boolean hasPrevious, boolean hasNext)
	{
		super(goalId, startTime, shouldSerializeDate, spread, Optional.of(totalActivityDurationMinutes), hasPrevious, hasNext);
		this.activityCategoryId = activityCategoryId;
		this.goalAccomplished = goalAccomplished;
		this.totalMinutesBeyondGoal = totalMinutesBeyondGoal;
	}

	@Override
	protected TemporalUnit getTimeUnit()
	{
		return ChronoUnit.DAYS;
	}

	@Override
	protected String formatDateAsIso(LocalDate date)
	{
		return formatDate(date);
	}

	@JsonIgnore
	public UUID getActivityCategoryId()
	{
		return activityCategoryId;
	}

	public boolean isGoalAccomplished()
	{
		return goalAccomplished;
	}

	public int getTotalMinutesBeyondGoal()
	{
		return totalMinutesBeyondGoal;
	}

	public static LocalDate parseDate(String iso8601)
	{
		try
		{
			return LocalDate.parse(iso8601, ISO8601_DAY_FORMATTER);
		}
		catch (DateTimeParseException e)
		{
			throw InvalidDataException.invalidDate(e, iso8601);
		}
	}

	public static String formatDate(LocalDate date)
	{
		return date.format(ISO8601_DAY_FORMATTER);
	}

	static DayActivityDto createInstance(DayActivity dayActivity, LevelOfDetail levelOfDetail)
	{
		return new DayActivityDto(dayActivity.getGoal().getId(), dayActivity.getGoal().getActivityCategory().getId(),
				dayActivity.getStartTime(), levelOfDetail == LevelOfDetail.DAY_DETAIL, getSpread(dayActivity, levelOfDetail),
				dayActivity.getTotalActivityDurationMinutes(), dayActivity.isGoalAccomplished(),
				dayActivity.getTotalMinutesBeyondGoal(), dayActivity.hasPrevious(), dayActivity.hasNext());
	}

	static DayActivityDto createInstanceInactivity(UserAnonymizedDto userAnonymized, GoalDto goal, ZonedDateTime startTime,
			LevelOfDetail levelOfDetail, Set<IntervalInactivityDto> missingInactivities)
	{
		missingInactivities.add(IntervalInactivityDto.createDayInstance(userAnonymized.getId(), goal.getGoalId(), startTime));
		return new DayActivityDto(goal.getGoalId(), goal.getActivityCategoryId(), startTime,
				levelOfDetail == LevelOfDetail.DAY_DETAIL,
				includeSpread(goal, levelOfDetail) ? createInactiveSpread() : Collections.emptyList(), 0, true, 0,
				IntervalActivityDto.hasPrevious(goal, startTime, ChronoUnit.DAYS),
				IntervalActivity.hasNext(startTime, ChronoUnit.DAYS));
	}

	static List<Integer> createInactiveSpread()
	{
		return new ArrayList<>(Collections.nCopies(96, 0));
	}

	private static List<Integer> getSpread(DayActivity dayActivity, LevelOfDetail levelOfDetail)
	{
		return includeSpread(dayActivity.getGoal(), levelOfDetail) ? dayActivity.getSpread() : Collections.emptyList();
	}

	private static boolean includeSpread(Goal goal, LevelOfDetail levelOfDetail)
	{
		return includeSpread(isTimeZoneGoal(goal), levelOfDetail);
	}

	private static boolean includeSpread(GoalDto goal, LevelOfDetail levelOfDetail)
	{
		return includeSpread(isTimeZoneGoal(goal), levelOfDetail);
	}

	private static boolean includeSpread(boolean isTimeZoneGoal, LevelOfDetail levelOfDetail)
	{
		return levelOfDetail == LevelOfDetail.DAY_DETAIL || levelOfDetail == LevelOfDetail.DAY_OVERVIEW && isTimeZoneGoal;
	}

	private static boolean isTimeZoneGoal(Goal goal)
	{
		return EntityUtil.enforceLoading(goal) instanceof TimeZoneGoal;
	}

	private static boolean isTimeZoneGoal(GoalDto goal)
	{
		return goal instanceof TimeZoneGoalDto;
	}
}
