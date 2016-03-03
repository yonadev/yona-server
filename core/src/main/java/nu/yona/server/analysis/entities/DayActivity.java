package nu.yona.server.analysis.entities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;

@Entity
@Table(name = "DAY_ACTIVITIES")
public class DayActivity extends IntervalActivity
{
	public static DayActivityRepository getRepository()
	{
		return (DayActivityRepository) RepositoryProvider.getRepository(DayActivity.class, UUID.class);
	}

	private LocalDate localDate;

	@OneToMany
	private List<Activity> activities;

	// Default constructor is required for JPA
	public DayActivity()
	{
		super(null, null, null);
	}

	public DayActivity(UUID id, UUID userAnonymizedID, UUID goalID, LocalDate localDate, List<Activity> activities)
	{
		super(id, userAnonymizedID, goalID);

		this.localDate = localDate;
		this.activities = activities;
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

	public static DayActivity createInstance(UUID userAnonymizedID, Goal goal, LocalDate localDate)
	{
		return new DayActivity(UUID.randomUUID(), userAnonymizedID, goal.getID(), localDate, new ArrayList<Activity>());
	}
}
