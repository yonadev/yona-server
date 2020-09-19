/*
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.goals.entities;

import java.util.Arrays;
import java.util.stream.IntStream;

import nu.yona.server.analysis.entities.DayActivity;

public interface ITimezoneGoal extends IGoal
{
	private int[] determineSpreadOutsideGoal(DayActivity dayActivity)
	{
		int[] spread = dayActivity.getSpread().stream().mapToInt(Integer::intValue).toArray();
		getSpreadCellsIntStream().forEach(i -> spread[i] = 0);
		return spread;
	}

	@Override
	default int computeTotalMinutesBeyondGoal(DayActivity dayActivity)
	{
		int[] spread = determineSpreadOutsideGoal(dayActivity);
		int sumOfSpreadOutsideGoal = Arrays.stream(spread).sum();
		// Due to rounding, the sum of the spread might be more than the total duration, so take the lowest of the two
		return Math.min(dayActivity.getTotalActivityDurationMinutes(), sumOfSpreadOutsideGoal);
	}

	@Override
	default boolean isGoalAccomplished(DayActivity dayActivity)
	{
		int[] spread = determineSpreadOutsideGoal(dayActivity);
		return !Arrays.stream(spread).anyMatch(i -> (i > 0));
	}

	default IntStream getSpreadCellsIntStream()
	{
		byte[] spreadCells = getSpreadCells();
		return IntStream.range(0, spreadCells.length).map(i -> spreadCells[i]);
	}

	byte[] getSpreadCells();
}
