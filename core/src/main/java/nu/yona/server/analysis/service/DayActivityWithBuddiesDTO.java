package nu.yona.server.analysis.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("dayActivityWithBuddies")
public class DayActivityWithBuddiesDTO
{
	private final Collection<ActivityForOneUser> activitiesByUser;
	private final UUID activityCategoryID;

	private DayActivityWithBuddiesDTO(UUID activityCategoryID, Collection<ActivityForOneUser> activitiesByUser)
	{
		this.activityCategoryID = activityCategoryID;
		this.activitiesByUser = activitiesByUser;
	}

	@JsonIgnore
	public UUID getActivityCategoryID()
	{
		return activityCategoryID;
	}

	@JsonIgnore
	public Collection<ActivityForOneUser> getDayActivitiesForUsers()
	{
		return Collections.unmodifiableCollection(activitiesByUser);
	}

	static DayActivityWithBuddiesDTO createInstance(UUID activityCategoryID, Collection<DayActivityDTO> dayActivities)
	{
		Collection<ActivityForOneUser> activitiesByUser = dayActivities.stream().map(da -> ActivityForOneUser.createInstance(da))
				.collect(Collectors.toList());
		return new DayActivityWithBuddiesDTO(activityCategoryID, activitiesByUser);
	}

	public static class ActivityForOneUser
	{
		private final boolean goalAccomplished;
		private final int totalMinutesBeyondGoal;
		private final List<Integer> spread;
		private final int totalActivityDurationMinutes;
		private final UUID goalID;

		private ActivityForOneUser(UUID goalID, List<Integer> spread, int totalActivityDurationMinutes, boolean goalAccomplished,
				int totalMinutesBeyondGoal)
		{
			this.goalID = goalID;
			this.spread = spread;
			this.totalActivityDurationMinutes = totalActivityDurationMinutes;
			this.goalAccomplished = goalAccomplished;
			this.totalMinutesBeyondGoal = totalMinutesBeyondGoal;
		}

		public static ActivityForOneUser createInstance(DayActivityDTO da)
		{
			return new ActivityForOneUser(da.getGoalID(), da.getSpread(), da.getTotalActivityDurationMinutes().get(),
					da.isGoalAccomplished(), da.getTotalMinutesBeyondGoal());
		}

		@JsonIgnore
		public UUID getGoalID()
		{
			return goalID;
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
