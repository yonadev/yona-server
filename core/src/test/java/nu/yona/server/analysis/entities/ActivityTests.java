/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class ActivityTests
{
	@Test
	public void setEndTime_afterComputeAggregates_resetsAreAggregatesComputed()
	{
		DayActivity dayActivityMock = mock(DayActivity.class);
		Activity activity = createActivity("21:30", "21:31");
		activity.setDayActivity(dayActivityMock);

		activity.setEndTime(activity.getEndTime().plusMinutes(5));

		verify(dayActivityMock, atLeastOnce()).resetAggregatesComputed();
	}

	@Test
	@Parameters({ "00:00, 00:01:59.999, 1", "01:00, 01:05, 5", "01:00:01, 01:05, 4", "01:00, 01:04:59, 4", "01:00, 02:01, 61" })
	public void getDurationMinutes_various_returnsTotalMinutes(String startTimeString, String endTimeString,
			int expectedTotalMinutes)
	{
		Activity activity = createActivity(startTimeString, endTimeString);

		assertThat(activity.getDurationMinutes(), equalTo(expectedTotalMinutes));
	}

	@Test
	public void getDurationMinutes_fullDay_returnsIncludingLastMinute()
	{
		ZonedDateTime startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
		Activity activity = Activity.createInstance(ZoneId.of("Europe/Amsterdam"), startOfDay.toLocalDateTime(),
				startOfDay.plusDays(1).toLocalDateTime(), Optional.empty());

		assertThat(activity.getDurationMinutes(), equalTo(1440));
	}

	private Activity createActivity(String startTimeString, String endTimeString)
	{
		return Activity.createInstance(ZoneId.of("Europe/Amsterdam"), getTimeOnDay(startTimeString).toLocalDateTime(),
				getTimeOnDay(endTimeString).toLocalDateTime(), Optional.empty());
	}

	protected ZonedDateTime getTimeOnDay(String timeString)
	{
		TemporalAccessor time = DateTimeFormatter.ISO_LOCAL_TIME.parse(timeString);
		return ZonedDateTime.now().withHour(time.get(ChronoField.HOUR_OF_DAY)).withMinute(time.get(ChronoField.MINUTE_OF_HOUR))
				.withSecond(time.get(ChronoField.SECOND_OF_MINUTE));
	}
}
