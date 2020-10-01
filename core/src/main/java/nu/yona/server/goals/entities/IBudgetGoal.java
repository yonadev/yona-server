/*
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.goals.entities;

import nu.yona.server.analysis.entities.DayActivity;

public interface IBudgetGoal extends IGoal
{
	@Override
	default boolean isGoalAccomplished(DayActivity dayActivity)
	{
		return dayActivity.getTotalActivityDurationMinutes() <= this.getMaxDurationMinutes();
	}

	@Override
	default int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		return Math.max(dayActivity.getTotalActivityDurationMinutes() - this.getMaxDurationMinutes(), 0);
	}

	int getMaxDurationMinutes();
}
