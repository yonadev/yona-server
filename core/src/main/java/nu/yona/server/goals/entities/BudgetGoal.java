package nu.yona.server.goals.entities;

import java.util.UUID;

import javax.persistence.Entity;

import nu.yona.server.analysis.entities.DayActivity;

@Entity
public class BudgetGoal extends Goal
{
	private int maxDurationMinutes;

	// Default constructor is required for JPA
	public BudgetGoal()
	{

	}

	private BudgetGoal(UUID id, ActivityCategory activityCategory, int maxDurationMinutes)
	{
		super(id, activityCategory);

		this.maxDurationMinutes = maxDurationMinutes;
	}

	public static BudgetGoal createNoGoInstance(ActivityCategory activityCategory)
	{
		return createInstance(activityCategory, 0);
	}

	public static BudgetGoal createInstance(ActivityCategory activityCategory, int maxDurationMinutes)
	{
		return new BudgetGoal(UUID.randomUUID(), activityCategory, maxDurationMinutes);
	}

	public int getMaxDurationMinutes()
	{
		return maxDurationMinutes;
	}

	public void setMaxDurationMinutes(int maxDurationMinutes)
	{
		this.maxDurationMinutes = maxDurationMinutes;
	}

	@Override
	public boolean isNoGoGoal()
	{
		return maxDurationMinutes <= 0;
	}

	@Override
	public boolean isMandatory()
	{
		return isNoGoGoal() && getActivityCategory().isMandatoryNoGo();
	}

	@Override
	public boolean isGoalAccomplished(DayActivity dayActivity)
	{
		return dayActivity.getTotalActivityDurationMinutes() < this.getMaxDurationMinutes();
	}

	@Override
	public int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		return Math.max(dayActivity.getTotalActivityDurationMinutes() - this.getMaxDurationMinutes(), 0);
	}
}
