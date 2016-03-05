package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.IntervalActivity;

@JsonRootName("intervalActivity")
public class IntervalActivityDTO
{
	private UUID goalID;
	private ZonedDateTime startTime;
	private ZonedDateTime endTime;
	private List<ActivityDTO> activities;

	private IntervalActivityDTO(UUID goalID, ZonedDateTime startTime, ZonedDateTime endTime, List<ActivityDTO> activities)
	{
		this.goalID = goalID;
		this.startTime = startTime;
		this.endTime = endTime;
		this.activities = activities;
	}

	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	public ZonedDateTime getEndTime()
	{
		return endTime;
	}

	@JsonIgnore
	public UUID getGoalID()
	{
		return goalID;
	}

	@JsonIgnore
	public List<ActivityDTO> getActivities()
	{
		return activities;
	}

	static IntervalActivityDTO createInstance(IntervalActivity intervalActivity)
	{
		return new IntervalActivityDTO(intervalActivity.getGoal().getID(), intervalActivity.getStartTime(),
				intervalActivity.getEndTime(), null);
	}
}
