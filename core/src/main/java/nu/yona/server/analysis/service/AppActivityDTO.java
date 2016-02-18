package nu.yona.server.analysis.service;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("appActivity")
public class AppActivityDTO
{
	private String application;
	private Date startTime;
	private Date endTime;

	@JsonCreator
	public AppActivityDTO(@JsonProperty("application") String application, @JsonProperty("startTime") Date startTime,
			@JsonProperty("endTime") Date endTime)
	{
		this.application = application;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public String getApplication()
	{
		return application;
	}

	public Date getStartTime()
	{
		return startTime;
	}

	public Date getEndTime()
	{
		return endTime;
	}
}
