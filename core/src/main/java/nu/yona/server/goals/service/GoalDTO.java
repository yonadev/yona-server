package nu.yona.server.goals.service;

import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.PROPERTY, property = "@class")
@JsonSubTypes(value = { @Type(value = BudgetGoalDTO.class, name = "budgetGoal") })
public abstract class GoalDTO
{
	private final UUID id;
	private String activityCategoryName;

	protected GoalDTO(UUID id, String activityCategoryName)
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

	public abstract Goal createGoalEntity();

	public static GoalDTO createInstance(Goal goal)
	{
		if (goal instanceof BudgetGoal)
		{
			return BudgetGoalDTO.createInstance((BudgetGoal) goal);
		}

		throw new NotImplementedException("GoalDTO creation not implemented yet for class " + goal.getClass());
	}
}
