/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IntervalInactivity
{
	public static final String DATE_PATTERN_WITH_ZONE = "yyyy-MM-dd'T'HH:mm:ss.SSSVV";
	private final Optional<UUID> userAnonymizedID;
	private final UUID goalID;
	private final ZonedDateTime startTime;
	private final ChronoUnit timeUnit;

	private IntervalInactivity(Optional<UUID> userAnonymizedID, UUID goalID, ZonedDateTime startTime, ChronoUnit timeUnit)
	{
		this.userAnonymizedID = userAnonymizedID;
		this.goalID = goalID;
		this.startTime = startTime;
		this.timeUnit = timeUnit;
	}

	@JsonCreator
	public IntervalInactivity(@JsonProperty("goalID") UUID goalID,
			@JsonFormat(pattern = DATE_PATTERN_WITH_ZONE) @JsonProperty("startTime") ZonedDateTime startTime,
			@JsonProperty("timeUnit") ChronoUnit timeUnit)
	{
		this(Optional.empty(), goalID, startTime, timeUnit);
	}

	@JsonIgnore
	public Optional<UUID> getUserAnonymizedID()
	{
		return userAnonymizedID;
	}

	public UUID getGoalID()
	{
		return goalID;
	}

	@JsonFormat(pattern = DATE_PATTERN_WITH_ZONE)
	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	public ChronoUnit getTimeUnit()
	{
		return timeUnit;
	}

	public static IntervalInactivity createWeekInstance(UUID userAnonymizedID, UUID goalID, ZonedDateTime startTime)
	{
		return new IntervalInactivity(Optional.of(userAnonymizedID), goalID, startTime, ChronoUnit.WEEKS);
	}

	public static IntervalInactivity createDayInstance(UUID userAnonymizedID, UUID goalID, ZonedDateTime startTime)
	{
		return new IntervalInactivity(Optional.of(userAnonymizedID), goalID, startTime, ChronoUnit.DAYS);
	}
}