package nu.yona.server.analysis.service;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.Activity;

@JsonRootName("activity")
public class ActivityDTO
{
	private Date startTime;
	private Date endTime;

	@JsonCreator
	public ActivityDTO(@JsonProperty("startTime") Date startTime, @JsonProperty("endTime") Date endTime)
	{
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public Date getStartTime()
	{
		return startTime;
	}

	public Date getEndTime()
	{
		return endTime;
	}

	static ActivityDTO createInstance(Activity activity)
	{
		return new ActivityDTO(activity.getStartTime(), activity.getEndTime());
	}
}
