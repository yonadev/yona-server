/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.DayOfWeek;
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
	public void testParse()
	{
		assertThat(WeekActivityDto.parseDate("2016-W02").getDayOfWeek(), equalTo(DayOfWeek.SUNDAY));
		assertThat(WeekActivityDto.parseDate("2016-W02").getYear(), equalTo(2016));
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
}
