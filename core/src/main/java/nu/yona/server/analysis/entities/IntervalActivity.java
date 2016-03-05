package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
public abstract class IntervalActivity extends EntityWithID
{
	@ManyToOne
	private Goal goal;

	@ManyToOne
	private UserAnonymized userAnonymized;

	private ZonedDateTime startTime;

	protected IntervalActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startTime)
	{
		super(id);
		this.userAnonymized = userAnonymized;
		this.goal = goal;
		this.startTime = startTime;
	}

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
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
