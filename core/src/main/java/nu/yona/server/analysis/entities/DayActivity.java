/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import org.hibernate.annotations.BatchSize;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.util.TimeUtil;

@Entity
public class DayActivity extends IntervalActivity
{
	@ManyToOne(fetch = FetchType.LAZY)
	private WeekActivity weekActivity;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "dayActivity", fetch = FetchType.LAZY)
	@BatchSize(size = 25) // When e.g. fetching a day or week overview, it is expected that only the last day is not aggregated,
							// so you get 4 goals * 1 day = 4 activity collections to be joined
	private List<Activity> activities;

	private boolean goalAccomplished;
	private int totalMinutesBeyondGoal;

	@Transient
	private List<ActivityInterval> nonoverlappingActivityIntervals;

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

	public static DayActivityRepository getRepository()
	{
		return (DayActivityRepository) RepositoryProvider.getRepository(DayActivity.class, Long.class);
	}

	public static DayActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZoneId timeZone, LocalDate startOfDay)
	{
		return new DayActivity(userAnonymized, goal, timeZone, startOfDay);
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
		return this.activities.stream().max((a, b) -> a.getEndTime().compareTo(b.getEndTime())).orElse(null);
	}

	public void addActivity(Activity activity)
	{
		activity.setDayActivity(this);
		activity.setActivityCategory(getGoal().getActivityCategory());
		this.activities.add(activity);

		this.resetAggregatesComputed();
	}

	@Override
	protected void resetAggregatesComputed()
	{
		super.resetAggregatesComputed();

		if (weekActivity == null)
		{
			// Occurs in unit tests only
			return;
		}
		weekActivity.resetAggregatesComputed();
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
		List<Integer> result = getEmptySpread();
		getNonoverlappingActivityIntervals().forEach(ai -> addToSpread(result, ai.startTime, ai.endTime));
		return result;
	}

	private void addToSpread(List<Integer> spread, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		// assumption:
		// - activities never start before or end after the day
		int spreadStartIndex = getSpreadIndex(startTime);
		int spreadEndIndex = getSpreadIndex(endTime);
		for (int spreadItemIndex = spreadStartIndex; spreadItemIndex <= spreadEndIndex; spreadItemIndex++)
		{
			if (spreadItemIndex > 95)
			{
				// if the activity ends at 00:00, this is counted as spread index 96
				// also, if the day has more than 24 hours, we ignore those extra hours in the spread
				break;
			}

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
		return (int) (Duration.between(getStartTime(), atTime).toMinutes() / 15);
	}

	@Override
	public void computeAggregates()
	{
		nonoverlappingActivityIntervals = null; // Ensure blank slate
		totalMinutesBeyondGoal = computeTotalMinutesBeyondGoal();
		goalAccomplished = computeGoalAccomplished();

		super.computeAggregates();
		nonoverlappingActivityIntervals = null; // Free up memory
	}

	private List<ActivityInterval> getNonoverlappingActivityIntervals()
	{
		if (nonoverlappingActivityIntervals == null)
		{
			List<Activity> sortedActivities = activities.stream()
					.sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime())).collect(Collectors.toList());
			nonoverlappingActivityIntervals = determineNonoverlappingActivityIntervals(sortedActivities);
		}
		return nonoverlappingActivityIntervals;
	}

	private static List<ActivityInterval> determineNonoverlappingActivityIntervals(List<Activity> sortedActivities)
	{
		// activities of different apps in the same activity category may overlap
		// or network activity and app activity may overlap

		List<ActivityInterval> result = new ArrayList<>(sortedActivities.size());
		ZonedDateTime previousEndTime = ZonedDateTime.of(LocalDateTime.MIN, ZoneOffset.UTC);
		for (Activity activity : sortedActivities)
		{
			ZonedDateTime startTime = TimeUtil.max(previousEndTime, activity.getStartTimeAsZonedDateTime());
			if (!startTime.isBefore(activity.getEndTimeAsZonedDateTime()))
			{
				// Activity fully overlaps previous one
				continue;
			}
			result.add(new ActivityInterval(startTime, activity.getEndTimeAsZonedDateTime()));
			previousEndTime = activity.getEndTimeAsZonedDateTime();
		}
		return result;
	}

	@Override
	protected int computeTotalActivityDurationMinutes()
	{
		return getNonoverlappingActivityIntervals().stream().map(ActivityInterval::getDurationMinutes).reduce(0, Integer::sum);
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

	private static class ActivityInterval
	{
		private final ZonedDateTime startTime;
		private final ZonedDateTime endTime;

		ActivityInterval(ZonedDateTime startTime, ZonedDateTime endTime)
		{
			assert !startTime.isAfter(endTime);
			this.startTime = startTime;
			this.endTime = endTime;
		}

		int getDurationMinutes()
		{
			return (int) Duration.between(startTime, endTime).toMinutes();
		}
	}
}
