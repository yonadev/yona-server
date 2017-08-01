/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.ZonedDateTime;

import org.junit.Test;

import nu.yona.server.goals.entities.Goal;

public class DayActivityTests extends IntervalActivityTestsBase
{
	private DayActivity createDayActivity()
	{
		return createDayActivity(budgetGoal);
	}

	private DayActivity createDayActivityTimeZoneGoal()
	{
		return createDayActivity(timeZoneGoal);
	}

	private DayActivity createDayActivity(Goal goal)
	{
		// pick a fixed test date 28 Feb 2017
		return DayActivity.createInstance(userAnonEntity, goal, testZone,
				ZonedDateTime.of(2017, 2, 28, 0, 0, 0, 0, testZone).toLocalDate());
	}

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
	public void getSpreadGetTotalActivityDurationMinutes_insideOneSpreadItem_rightResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");

		assertSpreadItemsAndTotal(d, "78=0,79=4,80=0", 4);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanTwoSpreadItems_rightResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "20:01");

		assertSpreadItemsAndTotal(d, "78=0,79=5,80=1,81=0", 6);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanTwoSpreadItemsStartEdge_rightResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:45", "20:01");

		assertSpreadItemsAndTotal(d, "78=0,79=15,80=1,81=0", 16);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanTwoSpreadItemsEndEdge_rightResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "20:15");

		assertSpreadItemsAndTotal(d, "78=0,79=5,80=15,81=0", 20);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_insideOneSpreadItemStartEndEdge_rightResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:45", "19:59:59");

		assertSpreadItemsAndTotal(d, "78=0,79=14,80=0", 14);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanThreeSpreadItems_rightResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "20:16");

		assertSpreadItemsAndTotal(d, "78=0,79=5,80=15,81=1,82=0", 21);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_multipleActivitiesOverlappingUnsorted_rightResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:48", "19:50");
		addActivity(d, "19:46", "19:59");
		addActivity(d, "20:01", "20:17");

		assertSpreadItemsAndTotal(d, "78=0,79=13,80=14,81=2,82=0", 31); // overlapping activities are counted twice in total count
	}

	@Test
	public void setEndTime_afterComputeAggregates_resetsAreAggregatesComputed()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		d.computeAggregates();

		d.getLastActivity().setEndTime(d.getLastActivity().getEndTime().plusMinutes(5));

		assertThat(d.areAggregatesComputed(), equalTo(false));
	}

	@Test
	public void addActivity_afterComputeAggregates_resetsAreAggregatesComputed()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		d.computeAggregates();

		addActivity(d, "20:05", "20:07");

		assertThat(d.areAggregatesComputed(), equalTo(false));
	}

	@Test
	public void addActivity_afterComputeAggregates_resetsAreAggregatesComputedOnWeek()
	{
		WeekActivity w = createWeekActivity();
		DayActivity d1 = createDayActivity(w, 0);
		w.addDayActivity(d1);
		d1.computeAggregates();
		w.computeAggregates();

		addActivity(d1, "20:05", "20:07");

		assertThat(w.areAggregatesComputed(), equalTo(false));
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_afterComputeAggregatesAddActivity_returnsRecomputedResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		d.computeAggregates();
		addActivity(d, "20:05", "20:07");

		assertSpreadItemsAndTotal(d, "78=0,79=4,80=2,81=0", 6);
	}

	@Test
	public void getTotalMinutesBeyondGoalIsGoalAccomplished_afterComputeAggregatesAddActivity_returnsRecomputedResults()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:00", "19:31");
		d.computeAggregates();
		addActivity(d, "20:00", "20:30");

		assertGoalMinutesBeyondAndAccomplished(d, 1, false);
	}

	@Test
	public void getTotalMinutesBeyondGoalIsGoalAccomplished_budgetGoalBelowBudget_zeroBeyondGoalAndGoalAccomplished()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");

		assertGoalMinutesBeyondAndAccomplished(d, 0, true);
	}

	@Test
	public void getTotalMinutesBeyondGoalIsGoalAccomplished_budgetGoalOnBudget_zeroBeyondGoalAndGoalAccomplished()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:00", "19:30");
		addActivity(d, "20:00", "20:30");

		assertGoalMinutesBeyondAndAccomplished(d, 0, true);
	}

	@Test
	public void getTotalMinutesBeyondGoalIsGoalAccomplished_budgetGoalOutsideBudget_rightNumberBeyondGoalAndGoalNotAccomplished()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:00", "19:31");
		addActivity(d, "20:00", "20:30");

		assertGoalMinutesBeyondAndAccomplished(d, 1, false);
	}

	@Test
	public void getTotalMinutesBeyondGoalIsGoalAccomplished_timeZoneGoalWithinAllowedZones_zeroBeyondGoalAndGoalAccomplished()
	{
		DayActivity d = createDayActivityTimeZoneGoal();
		addActivity(d, "05:00", "08:00");
		addActivity(d, "11:00", "12:30");

		assertGoalMinutesBeyondAndAccomplished(d, 0, true);
	}

	@Test
	public void getTotalMinutesBeyondGoalIsGoalAccomplished_timeZoneGoalCompletelyOutsideAllowedZones_rightNumberBeyondGoalAndGoalNotAccomplished()
	{
		DayActivity d = createDayActivityTimeZoneGoal();
		addActivity(d, "04:58", "04:59");
		addActivity(d, "12:31", "12:33");

		assertGoalMinutesBeyondAndAccomplished(d, 3, false);
	}

	@Test
	public void getTotalMinutesBeyondGoalIsGoalAccomplished_timeZoneGoalOutsideAndInsideAllowedZones_rightNumberBeyondGoalAndGoalNotAccomplished()
	{
		DayActivity d = createDayActivityTimeZoneGoal();
		addActivity(d, "04:59", "05:30");
		addActivity(d, "11:00", "13:00");

		assertGoalMinutesBeyondAndAccomplished(d, 31, false);
	}

	@Test
	public void areAggregatesComputed_default_returnsFalse()
	{
		DayActivity d = createDayActivity();

		boolean result = d.areAggregatesComputed();

		assertThat(result, equalTo(false));
	}

	@Test
	public void areAggregatesComputed_afterComputeAggregates_returnsTrue()
	{
		DayActivity d = createDayActivity();
		d.computeAggregates();

		boolean result = d.areAggregatesComputed();

		assertThat(result, equalTo(true));
	}

	private void assertGoalMinutesBeyondAndAccomplished(DayActivity d, int expectedTotalBeyondGoal,
			boolean expectedGoalAccomplished)
	{
		assertThat(d.areAggregatesComputed(), equalTo(false));
		assertThat(d.isGoalAccomplished(), equalTo(expectedGoalAccomplished));
		assertThat(d.getTotalMinutesBeyondGoal(), equalTo(expectedTotalBeyondGoal));

		d.computeAggregates();
		assertThat(d.isGoalAccomplished(), equalTo(expectedGoalAccomplished));
		assertThat(d.getTotalMinutesBeyondGoal(), equalTo(expectedTotalBeyondGoal));
	}
}
