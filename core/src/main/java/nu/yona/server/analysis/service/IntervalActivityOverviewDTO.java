/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class IntervalActivityOverviewDTO
{
	private final ZonedDateTime date;

	protected IntervalActivityOverviewDTO(ZonedDateTime date)
	{
		this.date = date;
	}

	/*
	 * The ISO-8601 week or day date.
	 */
	@JsonProperty("date")
	public String getDateStr()
	{
		return formatDateAsISO(date.toLocalDate());
	}

	/**
	 * The time zone in which the interval was recorded.
	 */
	public String getTimeZoneId()
	{
		return date.getZone().getId();
	}

	/**
	 * Format the date in compliance with ISO-8601
	 * 
	 * @param localDate The date to be formatted
	 * @return The formatted date
	 */
	protected abstract String formatDateAsISO(LocalDate localDate);
}
