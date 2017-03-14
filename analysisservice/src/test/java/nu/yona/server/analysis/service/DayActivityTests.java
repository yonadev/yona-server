/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.ZonedDateTime;

import org.junit.Test;

import nu.yona.server.analysis.entities.DayActivity;
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

	@Test
	public void testSpreadSpan1()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		assertSpreadItemsAndTotal(d, "78=0,79=4,80=0", 4);
	}

	@Test
	public void testSpreadSpan2()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "20:01");
		assertSpreadItemsAndTotal(d, "78=0,79=5,80=1,81=0", 6);
	}

	@Test
	public void testSpreadSpan2StartEdge()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:45", "20:01");
		assertSpreadItemsAndTotal(d, "78=0,79=15,80=1,81=0", 16);
	}

	@Test
	public void testSpreadSpan2EndEdge()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "20:15");
		assertSpreadItemsAndTotal(d, "78=0,79=5,80=15,81=0", 20);
	}

	@Test
	public void testSpreadSpan1StartEndEdge()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:45", "19:59:59");
		assertSpreadItemsAndTotal(d, "78=0,79=14,80=0", 14);
	}

	@Test
	public void testSpreadSpan3()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "20:16");
		assertSpreadItemsAndTotal(d, "78=0,79=5,80=15,81=1,82=0", 21);
	}

	@Test
	public void testSpreadMultipleActivitiesOverlappingUnsorted()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:48", "19:50");
		addActivity(d, "19:46", "19:59");
		addActivity(d, "20:01", "20:17");
		assertSpreadItemsAndTotal(d, "78=0,79=13,80=14,81=2,82=0", 31); // overlapping activities are counted twice in total count
	}

	@Test
	public void testSetActivityEndTimeResetsAggregatesComputed()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		d.computeAggregates();
		assertThat(d.areAggregatesComputed(), equalTo(true));

		d.getLastActivity().setEndTime(d.getLastActivity().getEndTime().plusMinutes(5));

		assertThat(d.areAggregatesComputed(), equalTo(false));
	}

	@Test
	public void testAddActivityResetsAggregatesComputed()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		d.computeAggregates();
		assertThat(d.areAggregatesComputed(), equalTo(true));

		addActivity(d, "20:05", "20:07");

		assertThat(d.areAggregatesComputed(), equalTo(false));
	}

	@Test
	public void testResetAggregatesComputedRecomputesSpreadAggregates()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		d.computeAggregates();

		addActivity(d, "20:05", "20:07");

		assertSpreadItemsAndTotal(d, "78=0,79=4,80=2,81=0", 6);
	}

	@Test
	public void testResetGoalAggregatesComputedRecomputesGoalAggregates()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:00", "19:31");
		d.computeAggregates();

		addActivity(d, "20:00", "20:30");

		assertGoalMinutesBeyondAndAccomplished(d, 1, false);
	}

	@Test
	public void testBudgetGoalBelowBudget()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55", "19:59");
		assertGoalMinutesBeyondAndAccomplished(d, 0, true);
	}

	@Test
	public void testBudgetGoalOnBudget()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:00", "19:30");
		addActivity(d, "20:00", "20:30");
		assertGoalMinutesBeyondAndAccomplished(d, 0, true);
	}

	@Test
	public void testBudgetGoalOutsideBudget()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:00", "19:31");
		addActivity(d, "20:00", "20:30");
		assertGoalMinutesBeyondAndAccomplished(d, 1, false);
	}

	@Test
	public void testTimeZoneGoalWithinAllowedZones()
	{
		DayActivity d = createDayActivityTimeZoneGoal();
		addActivity(d, "05:00", "08:00");
		addActivity(d, "11:00", "12:30");
		assertGoalMinutesBeyondAndAccomplished(d, 0, true);
	}

	@Test
	public void testTimeZoneGoalCompletelyOutsideAllowedZones()
	{
		DayActivity d = createDayActivityTimeZoneGoal();
		addActivity(d, "04:58", "04:59");
		addActivity(d, "12:31", "12:33");
		assertGoalMinutesBeyondAndAccomplished(d, 3, false);
	}

	@Test
	public void testTimeZoneGoalOutsideAndInsideAllowedZones()
	{
		DayActivity d = createDayActivityTimeZoneGoal();
		addActivity(d, "04:59", "05:30");
		addActivity(d, "11:00", "13:00");
		assertGoalMinutesBeyondAndAccomplished(d, 31, false);
	}

	private void assertGoalMinutesBeyondAndAccomplished(DayActivity d, int expectedTotalBeyondGoal,
			boolean expectedGoalAccomplished)
	{
		assertThat(d.areAggregatesComputed(), equalTo(false));
		assertThat(d.isGoalAccomplished(), equalTo(expectedGoalAccomplished));
		assertThat(d.getTotalMinutesBeyondGoal(), equalTo(expectedTotalBeyondGoal));

		d.computeAggregates();
		assertThat(d.areAggregatesComputed(), equalTo(true));
		assertThat(d.isGoalAccomplished(), equalTo(expectedGoalAccomplished));
		assertThat(d.getTotalMinutesBeyondGoal(), equalTo(expectedTotalBeyondGoal));
	}
}
