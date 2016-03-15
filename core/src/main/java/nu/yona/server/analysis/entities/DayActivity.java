package nu.yona.server.analysis.entities;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
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

	@ManyToOne
	private UserAnonymized userAnonymized;

	@OneToMany(cascade = CascadeType.ALL)
	private List<Activity> activities;

	private boolean goalAccomplished;

	// Default constructor is required for JPA
	public DayActivity()
	{
		super();
	}

	private DayActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfDay, List<Activity> activities,
			List<Integer> spread, int totalActivityDurationMinutes, boolean goalAccomplished, boolean aggregatesComputed)
	{
		super(id, goal, startOfDay, spread, totalActivityDurationMinutes, aggregatesComputed);

		this.userAnonymized = userAnonymized;
		this.activities = activities;
		this.goalAccomplished = goalAccomplished;
	}

	public UserAnonymized getUserAnonymized()
	{
		return this.userAnonymized;
	}

	@Override
	public ZonedDateTime getEndTime()
	{
		return getStartTime().plusDays(1);
	}

	@Override
	public ChronoUnit getTimeUnit()
	{
		return ChronoUnit.DAYS;
	}

	public Activity getLastActivity()
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

	@Override
	protected List<Integer> computeSpread()
	{
		List<Integer> result = getEmptySpread();
		// TODO: add from activities
		return result;
	}

	@Override
	protected int computeTotalActivityDurationMinutes()
	{
		return activities.stream().map(activity -> activity.getDurationMinutes()).reduce(0, Integer::sum);
	}

	public boolean isGoalAccomplished()
	{
		if (areAggregatesComputed())
		{
			return goalAccomplished;
		}

		return computeGoalAccomplished();
	}

	private boolean computeGoalAccomplished()
	{
		return this.getGoal().isGoalAccomplished(this);
	}

	public static DayActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfDay)
	{
		return new DayActivity(UUID.randomUUID(), userAnonymized, goal, startOfDay, new ArrayList<Activity>(),
				new ArrayList<Integer>(IntervalActivity.SPREAD_COUNT), 0, true, false);
	}
}
