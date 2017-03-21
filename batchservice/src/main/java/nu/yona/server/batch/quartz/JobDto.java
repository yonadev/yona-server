package nu.yona.server.batch.quartz;

import org.quartz.Job;
import org.quartz.JobDetail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("job")
public class JobDto
{
	private String group;
	private final String name;
	private final String description;
	private final Class<? extends Job> jobClass;

	@SuppressWarnings("unchecked")
	@JsonCreator
	public JobDto(@JsonProperty(value = "name", required = true) String name,
			@JsonProperty(value = "description", required = false) String description,
			@JsonProperty(value = "className", required = true) String className) throws ClassNotFoundException
	{
		this(null, name, description, (Class<? extends Job>) Class.forName(className));
	}

	public JobDto(String group, String name, String description, Class<? extends Job> jobClass)
	{
		this.setGroup(group);
		this.name = name;
		this.description = description;
		this.jobClass = jobClass;
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

	@JsonProperty("jobClass")
	public String getJobClassName()
	{
		return jobClass.getName();
	}

	@JsonIgnore
	public Class<? extends Job> getJobClass()
	{
		return jobClass;
	}

	static JobDto createInstance(JobDetail jobDetail)
	{
		return new JobDto(jobDetail.getKey().getGroup(), jobDetail.getKey().getName(), jobDetail.getDescription(),
				jobDetail.getJobClass());
	}
}
