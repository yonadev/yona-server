/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
public class WeekActivity extends IntervalActivity
{
	public static WeekActivityRepository getRepository()
	{
		return (WeekActivityRepository) RepositoryProvider.getRepository(WeekActivity.class, UUID.class);
	}

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super();
	}

	private WeekActivity(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startOfWeek, List<Integer> spread,
			int totalActivityDurationMinutes, boolean aggregatesComputed)
	{
		super(userAnonymized, goal, timeZone, startOfWeek, spread, totalActivityDurationMinutes, aggregatesComputed);
	}

	@Override
	protected TemporalUnit getTimeUnit()
	{
		return ChronoUnit.WEEKS;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartDate().plusDays(7).atStartOfDay(getTimeZone());
	}

	public List<DayActivity> getDayActivities()
	{
		List<DayActivity> dayActivities = DayActivity.getRepository().findActivitiesForUserAndGoalsInIntervalEndExcluded(
				getUserAnonymized().getID(), getGoal().getIDsIncludingHistoryItems(), getStartDate(), getEndTime().toLocalDate());

		if (dayActivities.size() > 7)
		{
			throw new IllegalStateException(
					"Invalid number of day activities in week starting at " + getStartDate() + ": " + dayActivities.size());
		}

		return dayActivities;
	}

	@Override
	protected List<Integer> computeSpread()
	{
		return getDayActivities().stream().map(dayActivity -> dayActivity.getSpread()).reduce(getEmptySpread(),
				(one, other) -> sumSpread(one, other));
	}

	private List<Integer> sumSpread(List<Integer> one, List<Integer> other)
	{
		List<Integer> result = new ArrayList<Integer>(IntervalActivity.SPREAD_COUNT);
		for (int i = 0; i < IntervalActivity.SPREAD_COUNT; i++)
		{
			result.add(one.get(i) + other.get(i));
		}
		return result;
	}

	@Override
	protected int computeTotalActivityDurationMinutes()
	{
		return getDayActivities().stream().map(dayActivity -> dayActivity.getTotalActivityDurationMinutes()).reduce(0,
				Integer::sum);
	}

	public static WeekActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startOfWeek)
	{
		return new WeekActivity(userAnonymized, goal, timeZone, startOfWeek,
				new ArrayList<Integer>(IntervalActivity.SPREAD_COUNT), 0, false);
	}
}
