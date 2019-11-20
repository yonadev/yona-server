/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalUnit;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;

@JsonRootName("weekActivity")
public class WeekActivityDto extends IntervalActivityDto
{
	private static final DateTimeFormatter ISO8601_WEEK_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive()
			.appendValue(IsoFields.WEEK_BASED_YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral("-W")
			.appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
			.parseDefaulting(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue()).toFormatter(Translator.EN_US_LOCALE);

	private final Map<DayOfWeek, DayActivityDto> dayActivities;

	private WeekActivityDto(UUID goalId, ZonedDateTime startTime, boolean shouldSerializeDate, List<Integer> spread,
			Optional<Integer> totalActivityDurationMinutes, Map<DayOfWeek, DayActivityDto> dayActivities, boolean hasPrevious,
			boolean hasNext)
	{
		super(goalId, startTime, shouldSerializeDate, spread, totalActivityDurationMinutes, hasPrevious, hasNext);
		this.dayActivities = dayActivities;
	}

	@Override
	protected TemporalUnit getTimeUnit()
	{
		return ChronoUnit.WEEKS;
	}

	@Override
	protected String formatDateAsIso(LocalDate date)
	{
		return formatDate(date);
	}

	@JsonIgnore
	public Map<DayOfWeek, DayActivityDto> getDayActivities()
	{
		return dayActivities;
	}

	public static LocalDate parseDate(String iso8601)
	{
		try
		{
			// ISO treats Monday as first day of the week, so our formatter defaults the day as Monday and here we subtract one
			// day to
			// return the preceding Sunday.
			return LocalDate.parse(iso8601, ISO8601_WEEK_FORMATTER).minusDays(1);
		}
		catch (DateTimeParseException e)
		{
			throw InvalidDataException.invalidDate(e, iso8601);
		}
	}

	public static String formatDate(LocalDate sundayDate)
	{
		if (sundayDate.getDayOfWeek() != DayOfWeek.SUNDAY)
		{
			throw new IllegalArgumentException("Passed date should be a Sunday");
		}

		// ISO treats Monday as first day of the week, so determine the week number based on the Monday rather than Sunday
		LocalDate mondayDate = sundayDate.plusDays(1);
		return ISO8601_WEEK_FORMATTER.format(mondayDate);
	}

	static WeekActivityDto createInstance(WeekActivity weekActivity, LevelOfDetail levelOfDetail)
	{
		boolean includeDetail = levelOfDetail == LevelOfDetail.WEEK_DETAIL;
		return new WeekActivityDto(weekActivity.getGoal().getId(), weekActivity.getStartTime(), includeDetail,
				includeDetail ? weekActivity.getSpread() : Collections.emptyList(),
				includeDetail ? Optional.of(weekActivity.getTotalActivityDurationMinutes()) : Optional.empty(),
				weekActivity.getDayActivities().stream()
						.collect(Collectors.toMap(dayActivity -> dayActivity.getStartDate().getDayOfWeek(),
								dayActivity -> DayActivityDto.createInstance(dayActivity, levelOfDetail))),
				weekActivity.hasPrevious(), weekActivity.hasNext());
	}

	public static WeekActivityDto createInstanceInactivity(UserAnonymizedDto userAnonymized, LocalDate earliestPossibleDate,
			Goal goal, ZonedDateTime startOfWeek, LevelOfDetail levelOfDetail, Set<IntervalInactivityDto> missingInactivities)
	{
		missingInactivities.add(IntervalInactivityDto.createWeekInstance(userAnonymized.getId(), goal.getId(), startOfWeek));
		boolean includeDetail = levelOfDetail == LevelOfDetail.WEEK_DETAIL;
		WeekActivityDto weekActivity = new WeekActivityDto(goal.getId(), startOfWeek, includeDetail,
				includeDetail ? DayActivityDto.createInactiveSpread() : Collections.emptyList(),
				includeDetail ? Optional.of(0) : Optional.empty(), new HashMap<>(),
				IntervalActivity.hasPrevious(goal, startOfWeek, ChronoUnit.WEEKS),
				IntervalActivity.hasNext(startOfWeek, ChronoUnit.WEEKS));
		weekActivity.createRequiredInactivityDays(userAnonymized, earliestPossibleDate,
				userAnonymized.getGoalsForActivityCategory(goal.getActivityCategory()), levelOfDetail, missingInactivities);
		return weekActivity;
	}

	public void createRequiredInactivityDays(UserAnonymizedDto userAnonymized, LocalDate earliestPossibleDate, Set<GoalDto> goals,
			LevelOfDetail levelOfDetail, Set<IntervalInactivityDto> missingInactivities)
	{
		// if the batch job has already run, skip
		if (dayActivities.size() == 7)
		{
			return;
		}
		ZonedDateTime earliestPossibleDateTime = earliestPossibleDate.atStartOfDay(userAnonymized.getTimeZone());
		// notice this doesn't take care of user time zone changes during the week
		// so for consistency it is important that the batch script adding inactivity does so
		for (int i = 0; i < 7; i++)
		{
			ZonedDateTime startOfDay = getStartTime().plusDays(i);
			if (startOfDay.isBefore(earliestPossibleDateTime))
			{
				continue;
			}
			if (isInFuture(startOfDay, getStartTime().getZone()))
			{
				break;
			}
			determineApplicableGoalForDay(goals, startOfDay).ifPresent(
					g -> addInactiveDayIfNoActivity(userAnonymized, g, startOfDay, levelOfDetail, missingInactivities));
		}
	}

	private static boolean isInFuture(ZonedDateTime startOfDay, ZoneId zone)
	{
		return startOfDay.isAfter(ZonedDateTime.now(zone));
	}

	private Optional<GoalDto> determineApplicableGoalForDay(Set<GoalDto> goals, ZonedDateTime startOfDay)
	{
		return goals.stream().filter(g -> g.wasActiveAtInterval(startOfDay, ChronoUnit.DAYS)).findAny();
	}

	private void addInactiveDayIfNoActivity(UserAnonymizedDto userAnonymized, GoalDto goal, ZonedDateTime startOfDay,
			LevelOfDetail levelOfDetail, Set<IntervalInactivityDto> missingInactivities)
	{
		DayOfWeek dayOfWeek = startOfDay.getDayOfWeek();
		if (dayActivities.containsKey(dayOfWeek))
		{
			return;
		}
		dayActivities.put(dayOfWeek,
				DayActivityDto.createInstanceInactivity(userAnonymized, goal, startOfDay, levelOfDetail, missingInactivities));
	}
}
