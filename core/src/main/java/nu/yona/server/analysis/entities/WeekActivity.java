package nu.yona.server.analysis.entities;

import java.time.LocalDate;
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

	private LocalDate firstDayLocalDate;

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super(null, null, null);
	}

	public WeekActivity(UUID id, UUID userAnonymizedID, UUID goalID, LocalDate firstDayLocalDate)
	{
		super(id, userAnonymizedID, goalID);

		this.firstDayLocalDate = firstDayLocalDate;
	}

	@Override
	public Date getStartTime()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getEndTime()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public static WeekActivity createInstance(UUID userAnonymizedID, Goal goal, LocalDate firstDayLocalDate)
	{
		return new WeekActivity(UUID.randomUUID(), userAnonymizedID, goal.getID(), firstDayLocalDate);
	}
}
