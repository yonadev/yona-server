/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.Test;

public class WeekActivityDtoTest
{
	@Test
	public void parseDate_iso8601DateFormat_returnsDateOfFirstDayOfWeek()
	{
		LocalDate parsedDate = WeekActivityDto.parseDate("2016-W02");

		assertThat(parsedDate.getDayOfWeek(), equalTo(DayOfWeek.SUNDAY));
		assertThat(parsedDate, equalTo(LocalDate.of(2016, 1, 10)));
	}

	@Test
	public void formatDate_default_returnsInIso8601WeekDateFormat()
	{
		String weekDate = WeekActivityDto.formatDate(LocalDate.of(2016, 1, 10));

		assertThat(weekDate, equalTo("2016-W02"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void formatDate_dateOnNonFirstDayOfWeek_throws()
	{
		WeekActivityDto.formatDate(LocalDate.of(2016, 1, 11));
	}

}
