package nu.yona.server.goals.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;

@JsonRootName("budgetGoal")
public class BudgetGoalDTO extends GoalDTO
{
	private final int maxDuration;

	@JsonCreator
	public BudgetGoalDTO(@JsonProperty("activityCategoryName") String activityCategoryName,
			@JsonProperty("maxDuration") int maxDuration)
	{
		this(null, activityCategoryName, maxDuration);
	}

	public BudgetGoalDTO(UUID id, String activityCategoryName, int maxDuration)
	{
		super(id, activityCategoryName);

		this.maxDuration = maxDuration;
	}

	public int getMaxDuration()
	{
		return maxDuration;
	}

	public static BudgetGoalDTO createInstance(BudgetGoal entity)
	{
		return new BudgetGoalDTO(entity.getID(), entity.getActivityCategory().getName(), entity.getMaxDuration());
	}

	public BudgetGoal createGoalEntity()
	{
		return BudgetGoal.createInstance(ActivityCategory.getRepository().findByName(this.getActivityCategoryName()),
				this.maxDuration);
	}
}
