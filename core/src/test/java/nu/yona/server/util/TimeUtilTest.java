/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TimeUtilTest
{
	@Test
	void getEndOfDay_anyTime_returnsTwelveOClockInGivenZone()
	{
		ZonedDateTime result = TimeUtil.getEndOfDay(ZoneId.of("Europe/Amsterdam"),
				ZonedDateTime.parse("2017-08-22T20:44:11+02:00[Europe/Paris]"));
		assertThat(result, equalTo(ZonedDateTime.parse("2017-08-23T00:00:00+02:00[Europe/Amsterdam]")));
	}

	@Test
	void getStartOfDay_anyTime_returnsZeroOClockInGivenZone()
	{
		ZonedDateTime result = TimeUtil.getStartOfDay(ZoneId.of("Europe/Amsterdam"),
				ZonedDateTime.parse("2017-08-22T20:44:11+02:00[Europe/Paris]"));
		assertThat(result, equalTo(ZonedDateTime.parse("2017-08-22T00:00:00+02:00[Europe/Amsterdam]")));
	}

	@Test
	void getStartOfWeek_anyTime_returnsSundayZeroOClockInGivenZone()
	{
		ZonedDateTime result = TimeUtil.getStartOfWeek(ZoneId.of("Europe/Amsterdam"),
				ZonedDateTime.parse("2017-08-22T20:44:11+02:00[Europe/Paris]"));
		assertThat(result, equalTo(ZonedDateTime.parse("2017-08-20T00:00:00+02:00[Europe/Amsterdam]")));
	}

	@Test
	void getStartOfWeek_anyDate_returnsSunday()
	{
		LocalDate result = TimeUtil.getStartOfWeek(LocalDate.parse("2017-08-22"));
		assertThat(result, equalTo(LocalDate.parse("2017-08-20")));
	}

	@ParameterizedTest
	@MethodSource("provideLocalDateValues")
	void maxLocalDate(LocalDate a, LocalDate b, LocalDate expected)
	{
		LocalDate result = TimeUtil.max(a, b);
		assertThat(result, equalTo(expected));
	}

	private static Stream<Arguments> provideLocalDateValues()
	{
		LocalDate now = TimeUtil.utcNow().toLocalDate();
		// @formatter:off
		return Stream.of(
				Arguments.of(now, now.minusDays(1), now),
				Arguments.of(now, now.plusDays(1), now.plusDays(1)),
				Arguments.of(now, now, now));
		// @formatter:on
	}

	@ParameterizedTest
	@MethodSource("provideZonedDateTimeValues")
	void maxZonedDateTime(ZonedDateTime a, ZonedDateTime b, ZonedDateTime expected)
	{
		ZonedDateTime result = TimeUtil.max(a, b);
		assertThat(result, equalTo(expected));
	}

	private static Stream<Arguments> provideZonedDateTimeValues()
	{
		ZonedDateTime now = TimeUtil.toUtcZonedDateTime(TimeUtil.utcNow());
		// @formatter:off
		return Stream.of(
				Arguments.of(now, now.minusSeconds(1), now),
				Arguments.of(now, now.plusSeconds(1), now.plusSeconds(1)),
				Arguments.of(now, now, now));
		// @formatter:on
	}
}
