/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.util.Require;

@Entity
public class WeekActivity extends IntervalActivity
{
	@OneToMany(cascade = CascadeType.ALL, mappedBy = "weekActivity", orphanRemoval = true)
	@BatchSize(size = 25) // When e.g. fetching a week overview with 3 weeks * 4 goals you already get 12 day collections to be
	// joined
	private final List<DayActivity> dayActivities = new ArrayList<>();

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super();
	}

	private WeekActivity(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startOfWeek)
	{
		super(userAnonymized, goal, timeZone, startOfWeek);
	}

	public static WeekActivityRepository getRepository()
	{
		return (WeekActivityRepository) RepositoryProvider.getRepository(WeekActivity.class, Long.class);
	}

	public static WeekActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startOfWeek)
	{
		Require.that(startOfWeek.getDayOfWeek() == DayOfWeek.SUNDAY, () -> YonaException
				.illegalState("Date " + startOfWeek + " is wrong. In Yona, Sunday is the first day of the week"));
		return new WeekActivity(userAnonymized, goal, timeZone, startOfWeek);
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

	@Override
	public void setGoal(Goal goal)
	{
		super.setGoal(goal);
		dayActivities.forEach(da -> da.setGoal(goal));
	}

	public List<DayActivity> getDayActivities()
	{
		if (dayActivities.size() > 7)
		{
			throw new IllegalStateException(
					"Invalid number of day activities in week starting at " + getStartDate() + ": " + dayActivities.size());
		}

		return Collections.unmodifiableList(dayActivities);
	}

	public void addDayActivity(DayActivity dayActivity)
	{
		if (dayActivities.size() >= 7)
		{
			throw new IllegalStateException(
					"Week starting at " + getStartDate() + " for goal with ID " + getGoal().getId() + " is already full ("
							+ dayActivities.size() + " days present) while trying to add day activity for " + dayActivity
							.getStartDate());
		}

		dayActivity.setWeekActivity(this);
		dayActivities.add(dayActivity);

		this.resetAggregatesComputed();
	}

	public void removeAllDayActivities()
	{
		dayActivities.forEach(da -> da.setWeekActivity(null));
		dayActivities.clear();
	}

	@Override
	protected List<Integer> computeSpread()
	{
		return getDayActivities().stream().map(DayActivity::getSpread).reduce(getEmptySpread(), this::sumSpread);
	}

	private List<Integer> sumSpread(List<Integer> one, List<Integer> other)
	{
		List<Integer> result = new ArrayList<>(IntervalActivity.SPREAD_COUNT);
		for (int i = 0; i < IntervalActivity.SPREAD_COUNT; i++)
		{
			result.add(one.get(i) + other.get(i));
		}
		return result;
	}

	@Override
	protected int computeTotalActivityDurationMinutes()
	{
		return getDayActivities().stream().map(DayActivity::getTotalActivityDurationMinutes).reduce(0, Integer::sum);
	}
}
