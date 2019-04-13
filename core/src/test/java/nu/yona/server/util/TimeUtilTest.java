/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

public class TimeUtilTest
{
	@Test
	public void getEndOfDay_anyTime_returnsTwelveOClockInGivenZone()
	{
		ZonedDateTime result = TimeUtil.getEndOfDay(ZoneId.of("Europe/Amsterdam"),
				ZonedDateTime.parse("2017-08-22T20:44:11+02:00[Europe/Paris]"));
		assertThat(result, equalTo(ZonedDateTime.parse("2017-08-23T00:00:00+02:00[Europe/Amsterdam]")));
	}

	@Test
	public void getStartOfDay_anyTime_returnsZeroOClockInGivenZone()
	{
		ZonedDateTime result = TimeUtil.getStartOfDay(ZoneId.of("Europe/Amsterdam"),
				ZonedDateTime.parse("2017-08-22T20:44:11+02:00[Europe/Paris]"));
		assertThat(result, equalTo(ZonedDateTime.parse("2017-08-22T00:00:00+02:00[Europe/Amsterdam]")));
	}

	@Test
	public void getStartOfWeek_anyTime_returnsSundayZeroOClockInGivenZone()
	{
		ZonedDateTime result = TimeUtil.getStartOfWeek(ZoneId.of("Europe/Amsterdam"),
				ZonedDateTime.parse("2017-08-22T20:44:11+02:00[Europe/Paris]"));
		assertThat(result, equalTo(ZonedDateTime.parse("2017-08-20T00:00:00+02:00[Europe/Amsterdam]")));
	}

	@Test
	public void getStartOfWeek_anyDate_returnsSunday()
	{
		LocalDate result = TimeUtil.getStartOfWeek(LocalDate.parse("2017-08-22"));
		assertThat(result, equalTo(LocalDate.parse("2017-08-20")));
	}
}
