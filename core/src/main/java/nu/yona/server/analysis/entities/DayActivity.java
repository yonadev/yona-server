package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.util.ArrayList;
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
@Table(name = "DAY_ACTIVITIES")
public class DayActivity extends IntervalActivity
{
	public static DayActivityRepository getRepository()
	{
		return (DayActivityRepository) RepositoryProvider.getRepository(DayActivity.class, UUID.class);
	}

	@OneToMany(cascade = CascadeType.ALL)
	private List<Activity> activities;

	// Default constructor is required for JPA
	public DayActivity()
	{
		super(null, null, null, null);
	}

	private DayActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfDay, List<Activity> activities)
	{
		super(id, userAnonymized, goal, startOfDay);
		this.activities = activities;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartTime().plusDays(1);
	}

	public Activity getLatestActivity()
	{
		if (this.activities.size() == 0)
		{
			return null;
		}

		return this.activities.get(this.activities.size() - 1);
	}

	public void addActivity(Activity activity)
	{
		this.activities.add(activity);
	}

	public static DayActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfDay)
	{
		return new DayActivity(UUID.randomUUID(), userAnonymized, goal, startOfDay, new ArrayList<Activity>());
	}
}
