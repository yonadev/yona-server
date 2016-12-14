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

public class IntervalInactivityDTO
{
	public static final String DATE_PATTERN_WITH_ZONE = "yyyy-MM-dd'T'HH:mm:ss.SSSVV";
	private final Optional<UUID> userAnonymizedId;
	private final UUID goalId;
	private final ZonedDateTime startTime;
	private final ChronoUnit timeUnit;

	private IntervalInactivityDTO(Optional<UUID> userAnonymizedId, UUID goalId, ZonedDateTime startTime, ChronoUnit timeUnit)
	{
		this.userAnonymizedId = userAnonymizedId;
		this.goalId = goalId;
		this.startTime = startTime;
		this.timeUnit = timeUnit;
	}

	@JsonCreator
	public IntervalInactivityDTO(@JsonProperty("goalId") UUID goalId,
			@JsonFormat(pattern = DATE_PATTERN_WITH_ZONE) @JsonProperty("startTime") ZonedDateTime startTime,
			@JsonProperty("timeUnit") ChronoUnit timeUnit)
	{
		this(Optional.empty(), goalId, startTime, timeUnit);
	}

	@JsonIgnore
	public Optional<UUID> getUserAnonymizedId()
	{
		return userAnonymizedId;
	}

	public UUID getGoalId()
	{
		return goalId;
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

	public static IntervalInactivityDTO createWeekInstance(UUID userAnonymizedId, UUID goalId, ZonedDateTime startTime)
	{
		return new IntervalInactivityDTO(Optional.of(userAnonymizedId), goalId, startTime, ChronoUnit.WEEKS);
	}

	public static IntervalInactivityDTO createDayInstance(UUID userAnonymizedId, UUID goalId, ZonedDateTime startTime)
	{
		return new IntervalInactivityDTO(Optional.of(userAnonymizedId), goalId, startTime, ChronoUnit.DAYS);
	}
}
