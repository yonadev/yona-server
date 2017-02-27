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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
public class DayActivity extends IntervalActivity
{
	public static DayActivityRepository getRepository()
	{
		return (DayActivityRepository) RepositoryProvider.getRepository(DayActivity.class, UUID.class);
	}

	@ManyToOne
	private WeekActivity weekActivity;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "dayActivity")
	private List<Activity> activities;

	private boolean goalAccomplished;
	private int totalMinutesBeyondGoal;

	// Default constructor is required for JPA
	public DayActivity()
	{
		super();
	}

	private DayActivity(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startOfDay)
	{
		super(userAnonymized, goal, timeZone, startOfDay);

		activities = new ArrayList<>();
	}

	@Override
	protected TemporalUnit getTimeUnit()
	{
		return ChronoUnit.DAYS;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartDate().atStartOfDay().plusDays(1).atZone(getTimeZone());
	}

	public List<Activity> getActivities()
	{
		return Collections.unmodifiableList(activities);
	}

	public Activity getLastActivity()
	{
		if (this.activities.size() == 0)
		{
			return null;
		}

		return this.activities.get(this.activities.size() - 1);
	}

	public void addActivity(Activity activity)
	{
		activity.setDayActivity(this);
		activity.setActivityCategory(getGoal().getActivityCategory());
		this.activities.add(activity);
	}

	public WeekActivity getWeekActivity()
	{
		return weekActivity;
	}

	public void setWeekActivity(WeekActivity weekActivity)
	{
		this.weekActivity = weekActivity;
	}

	@Override
	protected List<Integer> computeSpread()
	{
		// assumption:
		// - activities are not always sorted
		// - activities may overlap
		List<Integer> result = getEmptySpread();
		List<Activity> activitiesSorted = getActivitiesSorted();
		for (int i = 0; i < activitiesSorted.size(); i++)
		{
			Activity activity = activitiesSorted.get(i);

			// continue until no overlap
			ZonedDateTime activityBlockEndTime = activity.getEndTimeAsZonedDateTime();
			while (i + 1 < activitiesSorted.size()
					&& activitiesSorted.get(i + 1).getStartTimeAsZonedDateTime().isBefore(activityBlockEndTime))
			{
				// overlapping
				ZonedDateTime activityEndTime = activitiesSorted.get(i + 1).getEndTimeAsZonedDateTime();
				if (activityEndTime.isAfter(activityBlockEndTime))
				{
					// extend the block
					activityBlockEndTime = activityEndTime;
				}
				i++;
			}

			addToSpread(result, activity.getStartTimeAsZonedDateTime(), activityBlockEndTime);
		}
		return result;
	}

	private List<Activity> getActivitiesSorted()
	{
		List<Activity> activitiesSortedOnStartTime = activities.stream()
				.sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime())).collect(Collectors.toList());
		return activitiesSortedOnStartTime;
	}

	private void addToSpread(List<Integer> spread, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		// assumption:
		// - activities never start before or end after the day
		int spreadStartIndex = getSpreadIndex(startTime);
		int spreadEndIndex = getSpreadIndex(endTime);
		for (int spreadItemIndex = spreadStartIndex; spreadItemIndex <= spreadEndIndex; spreadItemIndex++)
		{
			int durationInSpreadItem = getDurationInSpreadItem(startTime, endTime, spreadStartIndex, spreadEndIndex,
					spreadItemIndex);
			spread.set(spreadItemIndex, spread.get(spreadItemIndex) + durationInSpreadItem);
		}
	}

	private int getDurationInSpreadItem(ZonedDateTime startTime, ZonedDateTime endTime, int spreadStartIndex, int spreadEndIndex,
			int spreadItemIndex)
	{
		if (spreadStartIndex == spreadEndIndex)
		{
			// Activity starts and ends inside the spread item
			return (int) startTime.until(endTime, ChronoUnit.MINUTES);
		}
		else if (spreadItemIndex == spreadStartIndex)
		{
			// Activity starts in the spread item and ends at the end of this item or after it
			return 15 - (startTime.getMinute() % 15);
		}
		else if (spreadItemIndex == spreadEndIndex)
		{
			// Activity starts at the begin of the spread item or before it and ends in this spread item
			return (endTime.getMinute() % 15);
		}
		else
		{
			// Activity starts at the begin of the spread item or before it and ends at the end of this item or after it
			return 15;
		}
	}

	private int getSpreadIndex(ZonedDateTime atTime)
	{
		return (atTime.getHour() * 4) + (atTime.getMinute() / 15);
	}

	@Override
	public void computeAggregates()
	{
		totalMinutesBeyondGoal = computeTotalMinutesBeyondGoal();
		goalAccomplished = computeGoalAccomplished();

		super.computeAggregates();
	}

	@Override
	protected int computeTotalActivityDurationMinutes()
	{
		return activities.stream().map(activity -> activity.getDurationMinutes()).reduce(0, Integer::sum);
	}

	public int getTotalMinutesBeyondGoal()
	{
		if (areAggregatesComputed())
		{
			return totalMinutesBeyondGoal;
		}

		return computeTotalMinutesBeyondGoal();
	}

	private int computeTotalMinutesBeyondGoal()
	{
		return this.getGoal().computeTotalMinutesBeyondGoal(this);
	}

	public boolean isGoalAccomplished()
	{
		if (areAggregatesComputed())
		{
			return goalAccomplished;
		}

		return computeGoalAccomplished();
	}

	private boolean computeGoalAccomplished()
	{
		return this.getGoal().isGoalAccomplished(this);
	}

	public static DayActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startOfDay)
	{
		return new DayActivity(userAnonymized, goal, timeZone, startOfDay);
	}
}
