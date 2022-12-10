/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import nu.yona.server.analysis.entities.DayActivity;

@Entity
public class BudgetGoal extends Goal implements IBudgetGoal
{
	private static final long serialVersionUID = -2070046758903687364L;

	private int maxDurationMinutes;

	// Default constructor is required for JPA
	public BudgetGoal()
	{

	}

	private BudgetGoal(UUID id, LocalDateTime creationTime, ActivityCategory activityCategory, int maxDurationMinutes)
	{
		super(id, creationTime, activityCategory);

		this.maxDurationMinutes = maxDurationMinutes;
	}

	private BudgetGoal(UUID id, BudgetGoal originalGoal, LocalDateTime endTime)
	{
		super(id, originalGoal, endTime);

		this.maxDurationMinutes = originalGoal.maxDurationMinutes;
	}

	public static BudgetGoal createNoGoInstance(LocalDateTime creationTime, ActivityCategory activityCategory)
	{
		return createInstance(creationTime, activityCategory, 0);
	}

	public static BudgetGoal createInstance(LocalDateTime creationTime, ActivityCategory activityCategory, int maxDurationMinutes)
	{
		return new BudgetGoal(UUID.randomUUID(), creationTime, activityCategory, maxDurationMinutes);
	}

	private static BudgetGoal createInstance(BudgetGoal originalGoal, LocalDateTime endTime)
	{
		return new BudgetGoal(UUID.randomUUID(), originalGoal, endTime);
	}

	@Override
	public int getMaxDurationMinutes()
	{
		return maxDurationMinutes;
	}

	public void setMaxDurationMinutes(int maxDurationMinutes)
	{
		this.maxDurationMinutes = maxDurationMinutes;
	}

	@Override
	public Goal cloneAsHistoryItem(LocalDateTime endTime)
	{
		return createInstance(this, endTime);
	}

	@Override
	public boolean isGoalAccomplished(DayActivity dayActivity)
	{
		return IBudgetGoal.super.isGoalAccomplished(dayActivity);
	}

	@Override
	public int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		return IBudgetGoal.super.computeTotalMinutesBeyondGoal(dayActivity);
	}
}
