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
	private final int maxDurationMinutes;

	@JsonCreator
	public BudgetGoalDTO(@JsonProperty("activityCategoryName") String activityCategoryName,
			@JsonProperty("maxDurationMinutes") int maxDurationMinutes)
	{
		this(null, activityCategoryName, maxDurationMinutes, false /* ignored */);
	}

	public BudgetGoalDTO(UUID id, String activityCategoryName, int maxDurationMinutes, boolean mandatory)
	{
		super(id, activityCategoryName, mandatory);

		this.maxDurationMinutes = maxDurationMinutes;
	}

	@Override
	public String getType()
	{
		return "BudgetGoal";
	}

	public int getMaxDurationMinutes()
	{
		return maxDurationMinutes;
	}

	public static BudgetGoalDTO createInstance(BudgetGoal entity)
	{
		return new BudgetGoalDTO(entity.getID(), entity.getActivityCategory().getName(), entity.getMaxDurationMinutes(),
				entity.isMandatory());
	}

	public BudgetGoal createGoalEntity()
	{
		ActivityCategory activityCategory = ActivityCategory.getRepository().findByName(this.getActivityCategoryName());
		if (activityCategory == null)
		{
			throw ActivityCategoryNotFoundException.notFoundByName(this.getActivityCategoryName());
		}
		return BudgetGoal.createInstance(activityCategory, this.maxDurationMinutes);
	}
}
