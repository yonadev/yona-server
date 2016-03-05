package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Entity
@Table(name = "WEEK_ACTIVITIES")
public class WeekActivity extends IntervalActivity
{
	public static WeekActivityRepository getRepository()
	{
		return (WeekActivityRepository) RepositoryProvider.getRepository(WeekActivity.class, UUID.class);
	}

	@OneToMany(cascade = CascadeType.ALL)
	private List<DayActivity> dayActivities;

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super(null, null, null, null);
	}

	public WeekActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime zonedStartOfWeek,
			List<DayActivity> dayActivities)
	{
		super(id, userAnonymized, goal, zonedStartOfWeek);

		this.dayActivities = dayActivities;
	}

	@Override
	public Date getEndTime()
	{
		return Date.from(getZonedStartTime().plusDays(7).toInstant());
	}

	public void addActivity(DayActivity dayActivity)
	{
		this.dayActivities.add(dayActivity);
	}

	public static WeekActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime zonedStartOfWeek)
	{
		return new WeekActivity(UUID.randomUUID(), userAnonymized, goal, zonedStartOfWeek, new ArrayList<DayActivity>());
	}
}
