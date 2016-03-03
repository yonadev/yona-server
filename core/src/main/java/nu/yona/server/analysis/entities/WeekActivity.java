package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;

@Entity
@Table(name = "WEEK_ACTIVITIES")
public class WeekActivity extends IntervalActivity
{
	public static WeekActivityRepository getRepository()
	{
		return (WeekActivityRepository) RepositoryProvider.getRepository(WeekActivity.class, UUID.class);
	}

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super(null, null, null, null);
	}

	public WeekActivity(UUID id, UUID userAnonymizedID, UUID goalID, ZonedDateTime zonedStartOfWeek)
	{
		super(id, userAnonymizedID, goalID, zonedStartOfWeek);
	}

	@Override
	public Date getEndTime()
	{
		return Date.from(getZonedStartTime().plusDays(7).toInstant());
	}

	public static WeekActivity createInstance(UUID userAnonymizedID, Goal goal, ZonedDateTime zonedStartOfWeek)
	{
		return new WeekActivity(UUID.randomUUID(), userAnonymizedID, goal.getID(), zonedStartOfWeek);
	}
}
