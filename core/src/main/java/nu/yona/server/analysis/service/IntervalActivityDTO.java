package nu.yona.server.analysis.service;

import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.goals.service.GoalDTO;

@JsonRootName("intervalActivity")
public class IntervalActivityDTO
{
	private GoalDTO goal;
	private Date startTime;
	private Date endTime;
	private Set<ActivityDTO> activities;

	public IntervalActivityDTO(GoalDTO goal, Date startTime, Date endTime, Set<ActivityDTO> activities)
	{
		this.goal = goal;
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
	public GoalDTO getGoal()
	{
		return goal;
	}

	@JsonIgnore
	public Set<ActivityDTO> getActivities()
	{
		return activities;
	}

	static IntervalActivityDTO createInstance(IntervalActivity intervalActivity)
	{
		return new IntervalActivityDTO(GoalDTO.createInstance(intervalActivity.getGoal()), intervalActivity.getStartTime(),
				intervalActivity.getEndTime(), null);
	}
}
