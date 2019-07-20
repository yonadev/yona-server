/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import nu.yona.server.exceptions.InvalidDataException;

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
	public void parseDate_invalidDateFormat_throwsInvalidDataException()
	{
		InvalidDataException ex = assertThrows(InvalidDataException.class, () -> WeekActivityDto.parseDate("2016-Wabc"));

		assertThat(ex.getMessageId(), equalTo("error.invalid.date"));

	}

	@Test
	public void formatDate_default_returnsInIso8601WeekDateFormat()
	{
		String weekDate = WeekActivityDto.formatDate(LocalDate.of(2016, 1, 10));

		assertThat(weekDate, equalTo("2016-W02"));
	}

	@Test
	public void formatDate_dateOnNonFirstDayOfWeek_throws()
	{
		assertThrows(IllegalArgumentException.class, () -> WeekActivityDto.formatDate(LocalDate.of(2016, 1, 11)));
	}

}
