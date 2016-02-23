package nu.yona.server.goals.service;

import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;
import org.springframework.hateoas.ResourceSupport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;

@JsonRootName("goal")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "@type")
@JsonSubTypes({ @Type(value = BudgetGoalDTO.class, name = "BudgetGoal") })
public abstract class GoalDTO extends ResourceSupport
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

	@JsonProperty(value = "@type")
	public abstract String getType();

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
