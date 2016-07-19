/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "WEEK_ACTIVITIES")
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

	private WeekActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek, List<Integer> spread,
			int totalActivityDurationMinutes, boolean aggregatesComputed)
	{
		super(id, userAnonymized, goal, startOfWeek, spread, totalActivityDurationMinutes, aggregatesComputed);
	}

	@Override
	protected TemporalUnit getTimeUnit()
	{
		return ChronoUnit.WEEKS;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartTime().plusDays(7);
	}

	public List<DayActivity> getDayActivities()
	{
		List<DayActivity> dayActivities = DayActivity.getRepository().findActivitiesForUserAndGoalInIntervalEndExcluded(
				getUserAnonymized().getID(), getGoal().getID(), getStartTime().toLocalDate(), getEndTime().toLocalDate());

		if (dayActivities.size() > 7)
		{
			throw new IllegalStateException(
					"Invalid number of day activities in week starting at " + getStartTime() + ": " + dayActivities.size());
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

	public static WeekActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek)
	{
		return new WeekActivity(UUID.randomUUID(), userAnonymized, goal, startOfWeek,
				new ArrayList<Integer>(IntervalActivity.SPREAD_COUNT), 0, false);
	}

	public Collection<DayActivity> createRequiredInactivityDays()
	{
		List<DayActivity> existingActivities = getDayActivities();
		Collection<DayActivity> newDayActivities = new ArrayList<>();
		// if the batch job has already run, skip
		if (existingActivities.size() == 7)
		{
			return Collections.emptyList();
		}
		// notice this doesn't take care of user time zone changes during the week
		// so for consistency it is important that the batch script adding inactivity does so
		for (int i = 0; i < 7; i++)
		{
			ZonedDateTime startOfDay = getStartTime().plusDays(i);
			if (isInFuture(startOfDay, getStartTime().getZone()))
			{
				break;
			}
			if (getGoal().wasActiveAtInterval(startOfDay, ChronoUnit.DAYS))
			{
				if (!existingActivities.stream()
						.anyMatch(dayActivity -> dayActivity.getDate().getDayOfWeek().equals(startOfDay.getDayOfWeek())))
				{
					newDayActivities.add(DayActivity.createInstanceInactivity(getUserAnonymized(), getGoal(), startOfDay));
				}
			}
		}
		return newDayActivities;
	}

	private static boolean isInFuture(ZonedDateTime startOfDay, ZoneId zone)
	{
		return startOfDay.isAfter(ZonedDateTime.now(zone));
	}
}
