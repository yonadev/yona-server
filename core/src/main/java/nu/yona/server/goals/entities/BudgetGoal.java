/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.util.UUID;

import javax.persistence.Entity;

@Entity
public class BudgetGoal extends Goal
{
	private int maxDuration;

	// Default constructor is required for JPA
	public BudgetGoal()
	{

	}

	private BudgetGoal(UUID id, ActivityCategory activityCategory, int maxDuration)
	{
		super(id, activityCategory);

		this.maxDuration = maxDuration;
	}

	public static BudgetGoal createNoGoInstance(ActivityCategory activityCategory)
	{
		return createInstance(activityCategory, 0);
	}

	public static BudgetGoal createInstance(ActivityCategory activityCategory, int maxDuration)
	{
		return new BudgetGoal(UUID.randomUUID(), activityCategory, maxDuration);
	}

	public int getMaxDuration()
	{
		return maxDuration;
	}

	public void setMaxDuration(int maxDuration)
	{
		this.maxDuration = maxDuration;
	}

	@Override
	public boolean isNoGoGoal()
	{
		return maxDuration <= 0;
	}

	@Override
	public boolean isMandatory()
	{
		return isNoGoGoal() && getActivityCategory().isMandatoryNoGo();
	}
}
