package nu.yona.server.analysis.entities;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "ACTIVITIES")
public class Activity extends EntityWithID
{
	public static ActivityRepository getRepository()
	{
		return (ActivityRepository) RepositoryProvider.getRepository(Activity.class, UUID.class);
	}

	private Date startTime;
	private Date endTime;

	// Default constructor is required for JPA
	public Activity()
	{
		super(null);
	}

	public Activity(UUID id, Date startTime, Date endTime)
	{
		super(id);
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public Date getStartTime()
	{
		return startTime;
	}

	public void setStartTime(Date startTime)
	{
		this.startTime = startTime;
	}

	public Date getEndTime()
	{
		return endTime;
	}

	public void setEndTime(Date endTime)
	{
		this.endTime = endTime;
	}

	public static Activity createInstance(Date startTime, Date endTime)
	{
		return new Activity(UUID.randomUUID(), startTime, endTime);
	}
}
