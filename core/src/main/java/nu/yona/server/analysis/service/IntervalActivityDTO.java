/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class IntervalActivityDTO
{
	public enum LevelOfDetail
	{
		WeekOverview, WeekDetail, DayOverview, DayDetail
	}

	private final UUID goalID;
	private final ZonedDateTime startTime;
	private final boolean shouldSerializeDate;

	private final List<Integer> spread;
	private final Optional<Integer> totalActivityDurationMinutes;

	protected IntervalActivityDTO(UUID goalID, ZonedDateTime startTime, boolean shouldSerializeDate, List<Integer> spread,
			Optional<Integer> totalActivityDurationMinutes)
	{
		this.goalID = goalID;
		this.startTime = startTime;
		this.shouldSerializeDate = shouldSerializeDate;
		this.spread = spread;
		this.totalActivityDurationMinutes = totalActivityDurationMinutes;
	}

	@JsonIgnore
	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	/**
	 * The ISO-8601 week or day date, or {null} if it should not be serialized.
	 */
	@JsonInclude(Include.NON_EMPTY)
	@JsonProperty("date")
	public String getDateIfRequired()
	{
		if (!shouldSerializeDate)
		{
			return null;
		}
		return getDate();
	}

	/*
	 * The ISO-8601 week or day date.
	 */
	@JsonIgnore
	public String getDate()
	{
		return formatDateAsISO(getStartTime().toLocalDate());
	}

	/**
	 * Format the date in compliance with ISO-8601
	 * 
	 * @param localDate The date to be formatted
	 * @return The formatted date
	 */
	protected abstract String formatDateAsISO(LocalDate localDate);

	/**
	 * The time zone in which the interval was recorded, or {null} if the date should not be serialized.
	 */
	@JsonInclude(Include.NON_EMPTY)
	public String getTimeZoneId()
	{
		if (!shouldSerializeDate)
		{
			return null;
		}
		return startTime.getZone().getId();
	}

	/*
	 * As the goal is already in the user profile, just a reference suffices. Notice that a goal may be removed; in that case it
	 * can be retrieved by the link.
	 */
	@JsonIgnore
	public UUID getGoalID()
	{
		return goalID;
	}

	@JsonInclude(Include.NON_EMPTY)
	public List<Integer> getSpread()
	{
		return spread;
	}

	@JsonInclude(Include.NON_NULL)
	public Integer getTotalActivityDurationMinutes()
	{
		return totalActivityDurationMinutes.orElse(null);
	}
}
