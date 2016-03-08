package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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

	@ManyToOne
	@JoinColumn(name = "user_anonymized_id")
	private UserAnonymized userAnonymized;

	@OneToMany(cascade = CascadeType.ALL)
	private List<DayActivity> dayActivities;

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super(null, null, null);
	}

	private WeekActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek,
			List<DayActivity> dayActivities)
	{
		super(id, goal, startOfWeek);

		this.userAnonymized = userAnonymized;
		this.dayActivities = dayActivities;
	}

	public UserAnonymized getUserAnonymized()
	{
		return userAnonymized;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartTime().plusDays(7);
	}

	public void addDayActivity(DayActivity dayActivity)
	{
		this.dayActivities.add(dayActivity);
	}

	public static WeekActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek)
	{
		return new WeekActivity(UUID.randomUUID(), userAnonymized, goal, startOfWeek, new ArrayList<DayActivity>());
	}
}
