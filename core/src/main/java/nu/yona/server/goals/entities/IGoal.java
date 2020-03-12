/*
 * Copyright (c) 2020 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.goals.entities;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.util.TimeUtil;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Optional;

public interface IGoal
{
	int computeTotalMinutesBeyondGoal(DayActivity dayActivity);

	boolean isGoalAccomplished(DayActivity dayActivity);

	default boolean wasActiveAtInterval(ZonedDateTime dateAtStartOfInterval, TemporalUnit timeUnit)
	{
		return wasActiveAtInterval(getCreationTimeNonOptional(), getEndTime(), dateAtStartOfInterval, timeUnit);
	}

	private boolean wasActiveAtInterval(LocalDateTime creationTime, Optional<LocalDateTime> endTime,
			ZonedDateTime dateAtStartOfInterval, TemporalUnit timeUnit)
	{
		LocalDateTime startNextInterval = TimeUtil.toUtcLocalDateTime(dateAtStartOfInterval.plus(1, timeUnit));
		return creationTime.isBefore(startNextInterval) && endTime.map(end -> end.isAfter(startNextInterval)).orElse(true);
	}

	LocalDateTime getCreationTimeNonOptional();

	Optional<LocalDateTime> getEndTime();
}
