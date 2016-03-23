/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "DAY_ACTIVITIES")
public class DayActivity extends IntervalActivity
{
	public static DayActivityRepository getRepository()
	{
		return (DayActivityRepository) RepositoryProvider.getRepository(DayActivity.class, UUID.class);
	}

	@ManyToOne
	private UserAnonymized userAnonymized;

	@OneToMany(cascade = CascadeType.ALL)
	private List<Activity> activities;

	private boolean goalAccomplished;
	private int totalMinutesBeyondGoal;

	// Default constructor is required for JPA
	public DayActivity()
	{
		super();
	}

	private DayActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfDay, List<Activity> activities,
			List<Integer> spread, int totalActivityDurationMinutes, boolean goalAccomplished, boolean aggregatesComputed)
	{
		super(id, goal, startOfDay, spread, totalActivityDurationMinutes, aggregatesComputed);

		this.userAnonymized = userAnonymized;
		this.activities = activities;
		this.goalAccomplished = goalAccomplished;
	}

	public UserAnonymized getUserAnonymized()
	{
		return this.userAnonymized;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartTime().plusDays(1);
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
		this.activities.add(activity);
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
			Date activityBlockEndTime = activity.getEndTime();
			while (i + 1 < activitiesSorted.size() && activitiesSorted.get(i + 1).getStartTime().before(activityBlockEndTime))
			{
				// overlapping
				Date activityEndTime = activitiesSorted.get(i + 1).getEndTime();
				if (activityEndTime.after(activityBlockEndTime))
				{
					// extend the block
					activityBlockEndTime = activityEndTime;
				}
				i++;
			}

			addToSpread(result, activity.getStartTime(), activityBlockEndTime);
		}
		return result;
	}

	private List<Activity> getActivitiesSorted()
	{
		List<Activity> activitiesSortedOnStartTime = activities.stream()
				.sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime())).collect(Collectors.toList());
		return activitiesSortedOnStartTime;
	}

	private void addToSpread(List<Integer> spread, Date activityBlockStartTime, Date activityBlockEndTime)
	{
		// assumption:
		// - activities never start before or end after the day
		ZoneId zone = getStartTime().getZone();
		ZonedDateTime startTime = activityBlockStartTime.toInstant().atZone(zone);
		ZonedDateTime endTime = activityBlockEndTime.toInstant().atZone(zone);
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
			// partial span
			return (int) startTime.until(endTime, ChronoUnit.MINUTES) + 1;
		}
		else if (spreadItemIndex == spreadStartIndex)
		{
			// start part
			return 15 - (startTime.getMinute() % 15);
		}
		else if (spreadItemIndex == spreadEndIndex)
		{
			// end part
			return 1 + (endTime.getMinute() % 15);
		}
		else
		{
			// total span
			return 15;
		}
	}

	private int getSpreadIndex(ZonedDateTime atTime)
	{
		return (atTime.getHour() * 4) + (atTime.getMinute() / 15);
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

	public static DayActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfDay)
	{
		return new DayActivity(UUID.randomUUID(), userAnonymized, goal, startOfDay, new ArrayList<Activity>(),
				new ArrayList<Integer>(IntervalActivity.SPREAD_COUNT), 0, true, false);
	}
}
