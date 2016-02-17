package nu.yona.server.goals.service;

import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import com.fasterxml.jackson.annotation.JsonIgnore;

import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;

public abstract class GoalDTO
{
	private final UUID id;
	private final String activityCategoryName;
	private final boolean mandatory;

	protected GoalDTO(UUID id, String activityCategoryName, boolean mandatory)
	{
		this.id = id;
		this.activityCategoryName = activityCategoryName;
		this.mandatory = mandatory;
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

	@JsonIgnore
	public boolean isMandatory()
	{
		return mandatory;
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
