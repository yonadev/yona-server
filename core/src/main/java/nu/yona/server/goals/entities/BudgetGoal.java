/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
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

	private BudgetGoal(UUID id, BudgetGoal originalGoal)
	{
		super(id, originalGoal);

		this.maxDurationMinutes = originalGoal.maxDurationMinutes;
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
	public Goal cloneAsHistoryItem()
	{
		return createInstance(this);
	}

	@Override
	public boolean isMandatory()
	{
		return isNoGoGoal() && getActivityCategory().isMandatoryNoGo();
	}

	@Override
	public boolean isNoGoGoal()
	{
		return maxDurationMinutes <= 0;
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

	public static BudgetGoal createNoGoInstance(ActivityCategory activityCategory)
	{
		return createInstance(activityCategory, 0);
	}

	public static BudgetGoal createInstance(ActivityCategory activityCategory, int maxDurationMinutes)
	{
		return new BudgetGoal(UUID.randomUUID(), activityCategory, maxDurationMinutes);
	}

	private static BudgetGoal createInstance(BudgetGoal originalGoal)
	{
		return new BudgetGoal(UUID.randomUUID(), originalGoal);
	}
}
