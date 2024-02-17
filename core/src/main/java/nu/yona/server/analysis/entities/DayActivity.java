/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import java.util.Optional;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.IGoal;
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

	public Optional<Activity> getLastActivity(UUID deviceAnonymizedId)
	{
		return this.activities.stream()
				.filter(a -> a.getDeviceAnonymized().map(DeviceAnonymized::getId).map(id -> id.equals(deviceAnonymizedId))
						.orElse(false)).max((a, b) -> a.getEndTime().compareTo(b.getEndTime()));
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
		// Convert to local date/time first, to ensure we always have exactly 24 hours in a day
		// Otherwise, the days we move in or out of daylight saving time would have 23 or 25 hours
		LocalDateTime atLocalTime = atTime.toLocalDateTime();
		return (int) (Duration.between(getStartTime().toLocalDateTime(), atLocalTime).toMinutes() / 15);
	}

	@Override
	public void computeAggregates()
	{
		nonoverlappingActivityIntervals = null; // Ensure blank slate
		Goal goal = getGoal();
		totalMinutesBeyondGoal = computeTotalMinutesBeyondGoal(goal);
		goalAccomplished = computeGoalAccomplished(goal);

		super.computeAggregates();
		nonoverlappingActivityIntervals = null; // Free up memory
	}

	private List<ActivityInterval> getNonoverlappingActivityIntervals()
	{
		if (nonoverlappingActivityIntervals == null)
		{
			List<Activity> sortedActivities = activities.stream()
					.sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime())).toList();
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

	public int getTotalMinutesBeyondGoal(IGoal goal)
	{
		if (areAggregatesComputed())
		{
			return totalMinutesBeyondGoal;
		}

		return computeTotalMinutesBeyondGoal(goal);
	}

	private int computeTotalMinutesBeyondGoal(IGoal goal)
	{
		return goal.computeTotalMinutesBeyondGoal(this);
	}

	public boolean isGoalAccomplished(IGoal goal)
	{
		if (areAggregatesComputed())
		{
			return goalAccomplished;
		}

		return computeGoalAccomplished(goal);
	}

	private boolean computeGoalAccomplished(IGoal goal)
	{
		return goal.isGoalAccomplished(this);
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
