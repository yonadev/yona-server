/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public abstract class IntervalActivityOverviewDTO
{
	private LocalDate date;

	protected IntervalActivityOverviewDTO(LocalDate date)
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

	/*
	 * The ISO-8601 date formatter.
	 */
	public abstract DateTimeFormatter getDateFormatter();
}
