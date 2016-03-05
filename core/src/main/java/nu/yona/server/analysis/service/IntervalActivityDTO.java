package nu.yona.server.analysis.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.IntervalActivity;

@JsonRootName("intervalActivity")
public class IntervalActivityDTO
{
	private UUID goalID;
	private Date startTime;
	private Date endTime;
	private List<ActivityDTO> activities;

	private IntervalActivityDTO(UUID goalID, Date startTime, Date endTime, List<ActivityDTO> activities)
	{
		this.goalID = goalID;
		this.startTime = startTime;
		this.endTime = endTime;
		this.activities = activities;
	}

	public Date getStartTime()
	{
		return startTime;
	}

	public Date getEndTime()
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
