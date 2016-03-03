package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.goals.entities.Goal;

@Entity
public abstract class IntervalActivity extends EntityWithID
{
	private UUID goalID;
	private UUID userAnonymizedID;
	private ZonedDateTime zonedStartTime;

	public IntervalActivity(UUID id, UUID userAnonymizedID, UUID goalID, ZonedDateTime zonedStartTime)
	{
		super(id);
		this.userAnonymizedID = userAnonymizedID;
		this.goalID = goalID;
		this.zonedStartTime = zonedStartTime;
	}

	public UUID getUserAnonymizedID()
	{
		return userAnonymizedID;
	}

	public UUID getGoalID()
	{
		return goalID;
	}

	public ZonedDateTime getZonedStartTime()
	{
		return zonedStartTime;
	}

	public Date getStartTime()
	{
		return Date.from(zonedStartTime.toInstant());
	}

	public abstract Date getEndTime();

	public Goal getGoal()
	{
		return Goal.getRepository().findOne(goalID);
	}
}
