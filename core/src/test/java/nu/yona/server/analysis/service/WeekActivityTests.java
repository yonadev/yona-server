/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.Test;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.WeekActivity;

public class WeekActivityTests extends IntervalActivityTestsBase
{
	private WeekActivity createWeekActivity()
	{
		// pick a fixed test date 26 Feb 2017
		return WeekActivity.createInstance(userAnonEntity, budgetGoal, testZone,
				ZonedDateTime.of(2017, 2, 26, 0, 0, 0, 0, testZone).toLocalDate());
	}

	private DayActivity createDayActivity(WeekActivity w, int plusDays)
	{
		return DayActivity.createInstance(userAnonEntity, w.getGoal(), testZone,
				w.getStartTime().plusDays(plusDays).toLocalDate());
	}

	@Test
	public void testParseWeekDate()
	{
		LocalDate parsedDate = WeekActivityDto.parseDate("2016-W02");

		assertThat(parsedDate.getDayOfWeek(), equalTo(DayOfWeek.SUNDAY));
		assertThat(parsedDate, equalTo(LocalDate.of(2016, 1, 10)));
	}

	@Test
	public void testFormatWeekDate()
	{
		String weekDate = WeekActivityDto.formatDate(LocalDate.of(2016, 1, 10));

		assertThat(weekDate, equalTo("2016-W02"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFormatWeekDateMondayThrows()
	{
		WeekActivityDto.formatDate(LocalDate.of(2016, 1, 11));
	}

	@Test
	public void testSpreadAndTotal()
	{
		WeekActivity w = createWeekActivity();
		DayActivity d1 = createDayActivity(w, 0);
		addActivity(d1, "19:50", "20:01");
		addActivity(d1, "20:16", "20:17");
		w.addDayActivity(d1);
		DayActivity d2 = createDayActivity(w, 1);
		addActivity(d2, "19:40", "19:55");
		w.addDayActivity(d2);

		assertSpreadItemsAndTotal(w, "77=0,78=5,79=20,80=1,81=1,82=0", 27);
	}

	@Test
	public void testResetAggregatesComputedRecomputesSpreadAggregates()
	{
		WeekActivity w = createWeekActivity();
		DayActivity d1 = createDayActivity(w, 0);
		addActivity(d1, "19:55", "19:59");
		w.addDayActivity(d1);
		d1.computeAggregates();
		w.computeAggregates();

		addActivity(d1, "20:05", "20:07");

		assertSpreadItemsAndTotal(w, "78=0,79=4,80=2,81=0", 6);
	}

	@Test
	public void testResetAggregatesComputedDayActivityResetsAggregatesComputed()
	{
		WeekActivity w = createWeekActivity();
		DayActivity d1 = createDayActivity(w, 0);
		w.addDayActivity(d1);
		d1.computeAggregates();
		w.computeAggregates();
		assertThat(w.areAggregatesComputed(), equalTo(true));

		addActivity(d1, "20:05", "20:07");

		assertThat(w.areAggregatesComputed(), equalTo(false));
	}

	@Test
	public void testAddDayActivityResetAggregatesComputed()
	{
		WeekActivity w = createWeekActivity();
		w.computeAggregates();
		assertThat(w.areAggregatesComputed(), equalTo(true));

		DayActivity d1 = createDayActivity(w, 0);
		addActivity(d1, "20:05", "20:07");
		w.addDayActivity(d1);

		assertThat(w.areAggregatesComputed(), equalTo(false));
	}
}
