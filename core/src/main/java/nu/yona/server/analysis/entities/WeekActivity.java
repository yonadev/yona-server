/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
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

	@ManyToOne
	private UserAnonymized userAnonymized;

	@OneToMany(cascade = CascadeType.ALL)
	private List<DayActivity> dayActivities;

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super();
	}

	private WeekActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek,
			List<DayActivity> dayActivities, List<Integer> spread, int totalActivityDurationMinutes, boolean aggregatesComputed)
	{
		super(id, goal, startOfWeek, spread, totalActivityDurationMinutes, aggregatesComputed);

		this.userAnonymized = userAnonymized;
		this.dayActivities = dayActivities;
	}

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartTime().plusDays(7);
	}

	public void addDayActivity(DayActivity dayActivity)
	{
		dayActivities.add(dayActivity);
	}

	public List<DayActivity> getDayActivities()
	{
		return dayActivities;
	}

	@Override
	protected List<Integer> computeSpread()
	{
		return dayActivities.stream().map(dayActivity -> dayActivity.getSpread()).reduce(getEmptySpread(),
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
		return dayActivities.stream().map(dayActivity -> dayActivity.getTotalActivityDurationMinutes()).reduce(0, Integer::sum);
	}

	public static WeekActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek)
	{
		return new WeekActivity(UUID.randomUUID(), userAnonymized, goal, startOfWeek, new ArrayList<DayActivity>(),
				new ArrayList<Integer>(IntervalActivity.SPREAD_COUNT), 0, false);
	}

	public static WeekActivity createInstanceInactivity(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek)
	{
		WeekActivity result = createInstance(userAnonymized, goal, startOfWeek);
		// leave days out
		return result;
	}
}
