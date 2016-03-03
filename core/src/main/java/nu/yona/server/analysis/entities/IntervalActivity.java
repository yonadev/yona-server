package nu.yona.server.analysis.entities;

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

	public IntervalActivity(UUID id, UUID userAnonymizedID, UUID goalID)
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

	public abstract Date getStartTime();

	public abstract Date getEndTime();

	public Goal getGoal()
	{
		return Goal.getRepository().findOne(goalID);
	}
}
