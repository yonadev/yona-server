package nu.yona.server.batch.quartz;

import java.util.Date;

import org.quartz.CronTrigger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("trigger")
public class CronTriggerDto
{
	private String group;
	private final String name;
	private final String description;
	private final String jobName;
	private final String cronExpression;
	private final String timeZone;
	private final Date nextFireTime;

	@JsonCreator
	public CronTriggerDto(@JsonProperty(value = "name", required = true) String name,
			@JsonProperty(value = "description", required = false) String description,
			@JsonProperty(value = "jobName", required = true) String jobName,
			@JsonProperty(value = "cronExpression", required = true) String cronExpression,
			@JsonProperty(value = "timeZone", required = true) String timeZone)
	{
		this(null, name, description, jobName, cronExpression, timeZone, null);
	}

	public CronTriggerDto(String group, String name, String description, String jobName, String cronExpression, String timeZone,
			Date nextFireTime)
	{
		this.setGroup(group);
		this.name = name;
		this.description = description;
		this.jobName = jobName;
		this.cronExpression = cronExpression;
		this.timeZone = timeZone;
		this.nextFireTime = nextFireTime;
	}

	public String getGroup()
	{
		return group;
	}

	public void setGroup(String group)
	{
		this.group = group;
	}

	public String getName()
	{
		return name;
	}

	public String getDescription()
	{
		return description;
	}

	public String getJobName()
	{
		return jobName;
	}

	public String getCronExpression()
	{
		return cronExpression;
	}

	public String getTimeZone()
	{
		return timeZone;
	}

	public Date getNextFireTime()
	{
		return nextFireTime;
	}

	static CronTriggerDto createInstance(CronTrigger trigger)
	{
		return new CronTriggerDto(trigger.getKey().getGroup(), trigger.getKey().getName(), trigger.getDescription(),
				trigger.getJobKey().getName(), trigger.getCronExpression(), trigger.getTimeZone().getID(),
				trigger.getNextFireTime());
	}
}
