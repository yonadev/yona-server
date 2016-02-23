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
		this(null, activityCategoryName, maxDuration, false /* ignored */);
	}

	public BudgetGoalDTO(UUID id, String activityCategoryName, int maxDuration, boolean mandatory)
	{
		super(id, activityCategoryName, mandatory);

		this.maxDuration = maxDuration;
	}

	@Override
	public String getType()
	{
		return "BudgetGoal";
	}

	public int getMaxDuration()
	{
		return maxDuration;
	}

	public static BudgetGoalDTO createInstance(BudgetGoal entity)
	{
		return new BudgetGoalDTO(entity.getID(), entity.getActivityCategory().getName(), entity.getMaxDuration(),
				entity.isMandatory());
	}

	public BudgetGoal createGoalEntity()
	{
		ActivityCategory activityCategory = ActivityCategory.getRepository().findByName(this.getActivityCategoryName());
		if (activityCategory == null)
		{
			throw ActivityCategoryNotFoundException.notFoundByName(this.getActivityCategoryName());
		}
		return BudgetGoal.createInstance(activityCategory, this.maxDuration);
	}
}
