/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public abstract class IntervalActivityDTO
{
	public enum LevelOfDetail
	{
		WeekOverview, WeekDetail, DayOverview, DayDetail
	}

	private UUID goalID;
	private ZonedDateTime startTime;

	private List<Integer> spread;
	private Integer totalActivityDurationMinutes;

	protected IntervalActivityDTO(UUID goalID, ZonedDateTime startTime, List<Integer> spread,
			Integer totalActivityDurationMinutes)
	{
		this.goalID = goalID;
		this.startTime = startTime;
		this.spread = spread;
		this.totalActivityDurationMinutes = totalActivityDurationMinutes;
	}

	@JsonIgnore
	public ZonedDateTime getStartTime()
	{
		return startTime;
	}

	/*
	 * The ISO-8601 week or day date.
	 */
	public abstract String getDate();

	/*
	 * The time zone in which the interval was recorded.
	 */
	public String getTimeZoneId()
	{
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

	@JsonInclude(Include.NON_NULL)
	public List<Integer> getSpread()
	{
		return spread;
	}

	@JsonInclude(Include.NON_NULL)
	public Integer getTotalActivityDurationMinutes()
	{
		return totalActivityDurationMinutes;
	}

	protected static <T> T includeIf(Callable<T> calculator, boolean condition)
	{
		if (condition)
		{
			try
			{
				return calculator.call();
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		return null;
	}
}
