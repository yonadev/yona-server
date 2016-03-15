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
@Table(name = "WEEK_ACTIVITIES")
public class WeekActivity extends IntervalActivity
{
	public static WeekActivityRepository getRepository()
	{
		return (WeekActivityRepository) RepositoryProvider.getRepository(WeekActivity.class, UUID.class);
	}

	@ManyToOne
	private UserAnonymized userAnonymized;

	@OneToMany(cascade = CascadeType.ALL)
	private List<DayActivity> dayActivities;

	// Default constructor is required for JPA
	public WeekActivity()
	{
		super(null, null, null, null, 0, false);
	}

	private WeekActivity(UUID id, UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek,
			List<DayActivity> dayActivities, int[] spread, int totalActivityDurationMinutes, boolean aggregatesComputed)
	{
		super(id, goal, startOfWeek, spread, totalActivityDurationMinutes, aggregatesComputed);

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

	@Override
	public ChronoUnit getTimeUnit()
	{
		return ChronoUnit.WEEKS;
	}

	public void addDayActivity(DayActivity dayActivity)
	{
		this.dayActivities.add(dayActivity);
	}

	@Override
	protected int[] computeSpread()
	{
		return this.dayActivities.stream().map(dayActivity -> dayActivity.getSpread())
				.reduce(new int[IntervalActivity.SPREAD_COUNT], (one, other) -> sumSpread(one, other));
	}

	private int[] sumSpread(int[] one, int[] other)
	{
		int[] result = new int[IntervalActivity.SPREAD_COUNT];
		for (int i = 0; i < IntervalActivity.SPREAD_COUNT; i++)
		{
			result[i] = one[i] + other[i];
		}
		return result;
	}

	@Override
	protected int computeTotalActivityDurationMinutes()
	{
		return this.dayActivities.stream().map(dayActivity -> dayActivity.getTotalActivityDurationMinutes()).reduce(0,
				Integer::sum);
	}

	public static WeekActivity createInstance(UserAnonymized userAnonymized, Goal goal, ZonedDateTime startOfWeek)
	{
		return new WeekActivity(UUID.randomUUID(), userAnonymized, goal, startOfWeek, new ArrayList<DayActivity>(),
				new int[IntervalActivity.SPREAD_COUNT], 0, false);
	}
}
