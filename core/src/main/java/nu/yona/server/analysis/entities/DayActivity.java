package nu.yona.server.analysis.entities;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
		// assumption:
		// - activities are not always sorted
		// - activities may overlap
		List<Activity> activitiesSortedOnStartTime = activities.stream()
				.sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime())).collect(Collectors.toList());
		for (int i = 0; i < activitiesSortedOnStartTime.size(); i++)
		{
			Activity activity = activitiesSortedOnStartTime.get(i);

			// continue until no overlap
			Date activityBlockEndTime = activity.getEndTime();
			while (i + 1 < activitiesSortedOnStartTime.size()
					&& activitiesSortedOnStartTime.get(i + 1).getStartTime().before(activityBlockEndTime))
			{
				// overlapping
				Date activityEndTime = activitiesSortedOnStartTime.get(i + 1).getEndTime();
				if (activityEndTime.after(activityBlockEndTime))
				{
					// extend the block
					activityBlockEndTime = activityEndTime;
				}
				i++;
			}

			addToSpread(result, activity.getStartTime(), activityBlockEndTime);
		}
		return result;
	}

	private void addToSpread(List<Integer> result, Date activityBlockStartTime, Date activityBlockEndTime)
	{
		// assumption:
		// - activities never start before or end after the day
		ZoneId zone = getStartTime().getZone();
		ZonedDateTime startTime = activityBlockStartTime.toInstant().atZone(zone);
		ZonedDateTime endTime = activityBlockEndTime.toInstant().atZone(zone);
		int spreadStartIndex = getSpreadIndex(startTime);
		int spreadEndIndex = getSpreadIndex(endTime);
		for (int spreadIndex = spreadStartIndex; spreadIndex <= spreadEndIndex; spreadIndex++)
		{
			int durationInSpreadItem;
			if (spreadStartIndex == spreadEndIndex)
			{
				// partial span
				durationInSpreadItem = (int) startTime.until(endTime, ChronoUnit.MINUTES);
			}
			else if (spreadIndex == spreadStartIndex)
			{
				// start part
				durationInSpreadItem = 15 - (startTime.getMinute() % 15);
			}
			else if (spreadIndex == spreadEndIndex)
			{
				// end part
				durationInSpreadItem = 1 + (endTime.getMinute() % 15);
			}
			else
			{
				// total span
				durationInSpreadItem = 15;
			}
			result.set(spreadIndex, result.get(spreadIndex) + durationInSpreadItem);
		}
	}

	private int getSpreadIndex(ZonedDateTime atTime)
	{
		return (atTime.getHour() * 4) + (atTime.getMinute() / 15);
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
