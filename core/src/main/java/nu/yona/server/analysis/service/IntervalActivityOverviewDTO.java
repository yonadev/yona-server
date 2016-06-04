/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class IntervalActivityOverviewDTO
{
	private ZonedDateTime date;

	protected IntervalActivityOverviewDTO(ZonedDateTime date)
	{
		this.date = date;
	}

	/*
	 * The ISO-8601 week or day date.
	 */
	public String getDate()
	{
		return date.format(getDateFormatter());
	}

	/**
	 * The time zone in which the interval was recorded.
	 */
	public String getTimeZoneId()
	{
		return date.getZone().getId();
	}

	/*
	 * The ISO-8601 date formatter.
	 */
	@JsonIgnore
	protected abstract DateTimeFormatter getDateFormatter();
}
