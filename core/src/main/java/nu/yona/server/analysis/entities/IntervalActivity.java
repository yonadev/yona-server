package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.goals.entities.Goal;

@MappedSuperclass
public abstract class IntervalActivity extends EntityWithID
{
	@ManyToOne
	private Goal goal;

	private ZonedDateTime startTime;

	protected IntervalActivity(UUID id, Goal goal, ZonedDateTime startTime)
	{
		super(id);
		this.goal = goal;
		this.startTime = startTime;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	public abstract ZonedDateTime getEndTime();
}
