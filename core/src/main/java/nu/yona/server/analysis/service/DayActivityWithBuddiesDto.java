/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("dayActivityWithBuddies")
public class DayActivityWithBuddiesDto
{
	private final Collection<ActivityForOneUser> activitiesByUser;
	private final UUID activityCategoryId;

	private DayActivityWithBuddiesDto(UUID activityCategoryId, Collection<ActivityForOneUser> activitiesByUser)
	{
		this.activityCategoryId = activityCategoryId;
		this.activitiesByUser = activitiesByUser;
	}

	@JsonIgnore
	public UUID getActivityCategoryId()
	{
		return activityCategoryId;
	}

	@JsonIgnore
	public Collection<ActivityForOneUser> getDayActivitiesForUsers()
	{
		return Collections.unmodifiableCollection(activitiesByUser);
	}

	static DayActivityWithBuddiesDto createInstance(UUID activityCategoryId, Collection<DayActivityDto> dayActivities)
	{
		Collection<ActivityForOneUser> activitiesByUser = dayActivities.stream().map(da -> ActivityForOneUser.createInstance(da))
				.toList();
		return new DayActivityWithBuddiesDto(activityCategoryId, activitiesByUser);
	}

	public static class ActivityForOneUser
	{
		private final boolean goalAccomplished;
		private final int totalMinutesBeyondGoal;
		private final List<Integer> spread;
		private final int totalActivityDurationMinutes;
		private final UUID goalId;

		private ActivityForOneUser(UUID goalId, List<Integer> spread, int totalActivityDurationMinutes, boolean goalAccomplished,
				int totalMinutesBeyondGoal)
		{
			this.goalId = goalId;
			this.spread = spread;
			this.totalActivityDurationMinutes = totalActivityDurationMinutes;
			this.goalAccomplished = goalAccomplished;
			this.totalMinutesBeyondGoal = totalMinutesBeyondGoal;
		}

		public static ActivityForOneUser createInstance(DayActivityDto da)
		{
			return new ActivityForOneUser(da.getGoalId(), da.getSpread(), da.getTotalActivityDurationMinutes().get(),
					da.isGoalAccomplished(), da.getTotalMinutesBeyondGoal());
		}

		@JsonIgnore
		public UUID getGoalId()
		{
			return goalId;
		}

		public boolean isGoalAccomplished()
		{
			return goalAccomplished;
		}

		public int getTotalMinutesBeyondGoal()
		{
			return totalMinutesBeyondGoal;
		}

		@JsonInclude(Include.NON_EMPTY)
		public List<Integer> getSpread()
		{
			return spread;
		}

		public int getTotalActivityDurationMinutes()
		{
			return totalActivityDurationMinutes;
		}
	}
}
