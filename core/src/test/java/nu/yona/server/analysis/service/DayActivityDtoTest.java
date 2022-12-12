/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import nu.yona.server.exceptions.InvalidDataException;

class DayActivityDtoTest
{
	@Test
	void parseDate_iso8601DateFormat_returnsParsedDate()
	{
		LocalDate parsedDate = DayActivityDto.parseDate("2016-01-11");

		assertThat(parsedDate, equalTo(LocalDate.of(2016, 1, 11)));
	}

	@Test
	void parseDate_invalidDateFormat_throwsInvalidDataException()
	{
		InvalidDataException ex = assertThrows(InvalidDataException.class, () -> DayActivityDto.parseDate("2016-01-abc"));

		assertThat(ex.getMessageId(), equalTo("error.invalid.date"));

	}

	@Test
	void formatDate_default_returnsInIso8601DateFormat()
	{
		String dayDate = DayActivityDto.formatDate(LocalDate.of(2016, 1, 11));

		assertThat(dayDate, equalTo("2016-01-11"));
	}
}
