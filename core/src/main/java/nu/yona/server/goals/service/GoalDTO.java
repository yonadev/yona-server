package nu.yona.server.goals.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;

@JsonRootName("goal")
public class GoalDTO
{
	private final UUID id;
	private String activityCategoryName;

	@JsonCreator
	public GoalDTO(@JsonProperty("activityCategoryName") String activityCategoryName)
	{
		this(null, activityCategoryName);
	}

	private GoalDTO(UUID id, String activityCategoryName)
	{
		this.id = id;
		this.activityCategoryName = activityCategoryName;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	public String getActivityCategoryName()
	{
		return activityCategoryName;
	}

	public static GoalDTO createInstance(Goal entity)
	{
		return new GoalDTO(entity.getID(), entity.getActivityCategory().getName());
	}

	public Goal createGoalEntity()
	{
		return Goal.createInstance(ActivityCategory.getRepository().findByName(activityCategoryName));
	}
}
