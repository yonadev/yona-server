/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.LocalDate;

import org.junit.Test;

public class DayActivityDtoTests
{
	@Test
	public void parseDate_default_returnsRightResult()
	{
		LocalDate parsedDate = DayActivityDto.parseDate("2016-01-11");

		assertThat(parsedDate, equalTo(LocalDate.of(2016, 1, 11)));
	}

	@Test
	public void formatDate_default_returnsRightResult()
	{
		String dayDate = DayActivityDto.formatDate(LocalDate.of(2016, 1, 11));

		assertThat(dayDate, equalTo("2016-01-11"));
	}
}
