package nu.yona.server.analysis.entities;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;

@Entity
@Table(name = "ACTIVITIES")
public class Activity extends EntityWithID
{
	public static ActivityRepository getRepository()
	{
		return (ActivityRepository) RepositoryProvider.getRepository(Activity.class, UUID.class);
	}

	private UUID userAnonymizedID;
	private UUID goalID;
	private Date startTime;
	private Date endTime;

	// Default constructor is required for JPA
	public Activity()
	{
		super(null);
	}

	public Activity(UUID id, UUID userAnonymizedID, UUID goalID)
	{
		super(id);
		this.userAnonymizedID = userAnonymizedID;
		this.goalID = goalID;
	}

	public UUID getUserAnonymizedID()
	{
		return userAnonymizedID;
	}

	public UUID getGoalID()
	{
		return goalID;
	}

	public Date getStartTime()
	{
		return startTime;
	}

	public Date getEndTime()
	{
		return endTime;
	}

	public void setEndTime(Date endTime)
	{
		this.endTime = endTime;
	}

	public static Activity createInstance(UUID userAnonymizedID, Goal goal)
	{
		return new Activity(UUID.randomUUID(), userAnonymizedID, goal.getID());
	}

	public Goal getGoal()
	{
		return Goal.getRepository().findOne(goalID);
	}
}
