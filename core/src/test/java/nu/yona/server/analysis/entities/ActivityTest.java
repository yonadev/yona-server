/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import nu.yona.server.Translator;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;

class ActivityTest
{
	private DeviceAnonymized deviceAnonEntity;

	@BeforeEach
	public void setUp()
	{
		deviceAnonEntity = DeviceAnonymized.createInstance(0, OperatingSystem.ANDROID, "Unknown", 0, Optional.empty(),
				Translator.EN_US_LOCALE);
	}

	@Test
	void setEndTime_afterComputeAggregates_resetsAreAggregatesComputed()
	{
		DayActivity dayActivityMock = mock(DayActivity.class);
		Activity activity = createActivity("21:30", "21:31");
		activity.setDayActivity(dayActivityMock);
		dayActivityMock.computeAggregates();

		activity.setEndTime(activity.getEndTime().plusMinutes(5));

		verify(dayActivityMock, atLeastOnce()).resetAggregatesComputed();
	}

	@ParameterizedTest
	@CsvSource({ "00:00:00, 00:00:59, 0", "00:00, 00:01:59.999, 1", "01:00, 01:05, 5", "01:00:01, 01:05, 4", "01:00, 01:04:59, 4",
			"01:00, 02:01, 61" })
		// Note that the analysis service will never create activities shorter than one minute
	void getDurationMinutes_various_returnsTotalMinutes(String startTimeString, String endTimeString, int expectedTotalMinutes)
	{
		Activity activity = createActivity(startTimeString, endTimeString);

		assertThat(activity.getDurationMinutes(), equalTo(expectedTotalMinutes));
	}

	@Test
	void getDurationMinutes_fullDay_returnsIncludingLastMinute()
	{
		ZonedDateTime startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
		Activity activity = Activity.createInstance(deviceAnonEntity, ZoneId.of("Europe/Amsterdam"), startOfDay.toLocalDateTime(),
				startOfDay.plusDays(1).toLocalDateTime(), Optional.empty());

		assertThat(activity.getDurationMinutes(), equalTo(1440));
	}

	private Activity createActivity(String startTimeString, String endTimeString)
	{
		return Activity.createInstance(deviceAnonEntity, ZoneId.of("Europe/Amsterdam"),
				getTimeOnDay(startTimeString).toLocalDateTime(), getTimeOnDay(endTimeString).toLocalDateTime(), Optional.empty());
	}

	protected ZonedDateTime getTimeOnDay(String timeString)
	{
		TemporalAccessor time = DateTimeFormatter.ISO_LOCAL_TIME.parse(timeString);
		return ZonedDateTime.now().withHour(time.get(ChronoField.HOUR_OF_DAY)).withMinute(time.get(ChronoField.MINUTE_OF_HOUR))
				.withSecond(time.get(ChronoField.SECOND_OF_MINUTE));
	}
}
