package nu.yona.server.goals.entities;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "GOALS")
public class Goal extends EntityWithID
{
	public static GoalRepository getRepository()
	{
		return (GoalRepository) RepositoryProvider.getRepository(Goal.class, UUID.class);
	}

	@ManyToOne
	private ActivityCategory activityCategory;

	// Default constructor is required for JPA
	public Goal()
	{
		super(null);
	}

	private Goal(UUID id, ActivityCategory activityCategory)
	{
		super(id);

		if (activityCategory == null)
		{
			throw new IllegalArgumentException("activityCategory cannot be null");
		}
		this.activityCategory = activityCategory;
	}

	public ActivityCategory getActivityCategory()
	{
		return activityCategory;
	}

	public static Goal createInstance(ActivityCategory activityCategory)
	{
		return new Goal(UUID.randomUUID(), activityCategory);
	}
}
