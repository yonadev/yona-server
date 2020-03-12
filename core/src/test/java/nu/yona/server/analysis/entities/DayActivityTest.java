/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;

import nu.yona.server.goals.entities.IGoal;
import org.junit.jupiter.api.Test;

import nu.yona.server.goals.entities.Goal;

public class DayActivityTest extends IntervalActivityTestBase
{
	private DayActivity createDayActivity()
	{
		return createDayActivity(budgetGoal);
	}

	private DayActivity createDayActivity(ZonedDateTime dateTime)
	{
		return createDayActivity(dateTime, budgetGoal);
	}

	private DayActivity createDayActivityTimeZoneGoal()
	{
		return createDayActivity(timeZoneGoal);
	}

	private DayActivity createDayActivity(Goal goal)
	{
		// pick a fixed test date 28 Feb 2017
		return createDayActivity(ZonedDateTime.of(2017, 2, 28, 0, 0, 0, 0, testZone), goal);
	}

	private DayActivity createDayActivity(ZonedDateTime dateTime, Goal goal)
	{
		return DayActivity.createInstance(userAnonEntity, goal, testZone, dateTime.toLocalDate());
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
	public void getSpread_beforeTransitionIntoDst_noCorrectionApplied()
	{
		DayActivity d = createDayActivity(ZonedDateTime.of(2018, 3, 25, 0, 0, 0, 0, testZone));
		addActivity(d, "01:05:00", "01:07:00");

		assertSpreadItems(d.getSpread(), "3=0,4=2,5=0");
	}

	@Test
	public void getSpread_duringTransitionIntoDst_negativeCorrectionApplied()
	{
		// This is a hypothetical situation, as the clock jumps from 2:00 to 3:00
		DayActivity d = createDayActivity(ZonedDateTime.of(2018, 3, 25, 0, 0, 0, 0, testZone));
		addActivity(d, "02:05:00", "02:07:00");

		assertSpreadItems(d.getSpread(), "11=0,12=2,13=0");
	}

	@Test
	public void getSpread_afterTransitionIntoDst_negativeCorrectionApplied()
	{
		DayActivity d = createDayActivity(ZonedDateTime.of(2018, 3, 25, 0, 0, 0, 0, testZone));
		addActivity(d, "03:05:00", "03:07:00");

		assertSpreadItems(d.getSpread(), "11=0,12=2,13=0");
	}

	@Test
	public void getSpread_beforeTransitionOutOfDst_noCorrectionApplied()
	{
		DayActivity d = createDayActivity(ZonedDateTime.of(2018, 10, 28, 0, 0, 0, 0, testZone));
		addActivity(d, "01:05:00", "01:07:00");

		assertSpreadItems(d.getSpread(), "3=0,4=2,5=0");
	}

	@Test
	public void getSpread_afterTransitionOutOfDst_positiveCorrectionApplied()
	{
		DayActivity d = createDayActivity(ZonedDateTime.of(2018, 10, 28, 0, 0, 0, 0, testZone));
		addActivity(d, "04:05:00", "04:07:00");

		assertSpreadItems(d.getSpread(), "15=0,16=2,17=0");
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_lessThanAMinute_ignored()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "20:05:11", "20:06:08");

		assertSpreadItemsAndTotal(d, "79=0,80=0,81=0", 0);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_endOfDayIncluded_includesEndOfDay()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(deviceAnonEntity, testZone, getTimeOnDay(d, "23:50").toLocalDateTime(),
				d.getStartTime().plusDays(1).toLocalDateTime(), Optional.empty()));

		assertSpreadItemsAndTotal(d, "95=10", 10);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_moreThanAMinute_roundedDownToMinutes()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55:08", "19:59:03");

		assertSpreadItemsAndTotal(d, "78=0,79=3,80=0", 3);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanTwoSpreadItems_resultsInBothSpreadItems()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55:00", "20:01:00");

		assertSpreadItemsAndTotal(d, "78=0,79=5,80=1,81=0", 6);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_startEdge_isIncluded()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:45:00", "19:50:00");

		assertSpreadItemsAndTotal(d, "78=0,79=5,80=0", 5);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_endEdge_isNotIncluded()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55:00", "19:59:59");

		assertSpreadItemsAndTotal(d, "78=0,79=4,80=0", 4);
	}

	public void getSpreadGetTotalActivityDurationMinutes_startEdgeOfNextSpreadItem_isIncluded()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55:00", "20:00:00");

		assertSpreadItemsAndTotal(d, "78=0,79=5,80=0", 5);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanTwoSpreadItemsSecondItemLessThanAMinute_ignoredInSecondItem()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "20:05:08", "20:15:03");

		assertSpreadItemsAndTotal(d, "79=0,80=10,81=0", 9);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanTwoSpreadItemsFirstItemLessThanAMinute_upgradedToOneMinuteInFirstItem()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:59:59", "20:05:03");

		assertSpreadItemsAndTotal(d, "78=0,79=1,80=5,81=0", 5);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_spanThreeSpreadItems_resultsInAllThreeSpreadItems()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:55:00", "20:16:00");

		assertSpreadItemsAndTotal(d, "78=0,79=5,80=15,81=1,82=0", 21);
	}

	@Test
	public void getSpreadGetTotalActivityDurationMinutes_multipleActivitiesOverlappingUnsorted_countsOverlappingActivitiesOnce()
	{
		DayActivity d = createDayActivity();
		addActivity(d, "19:48:00", "19:50:00");
		addActivity(d, "19:46:00", "19:59:00");
		addActivity(d, "20:01:00", "20:17:00");

		assertSpreadItemsAndTotal(d, "78=0,79=13,80=14,81=2,82=0", 29);
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
		IGoal goal = d.getGoal();
		assertThat(d.areAggregatesComputed(), equalTo(false));
		assertThat(d.isGoalAccomplished(goal), equalTo(expectedGoalAccomplished));
		assertThat(d.getTotalMinutesBeyondGoal(goal), equalTo(expectedTotalBeyondGoal));

		d.computeAggregates();
		assertThat(d.isGoalAccomplished(goal), equalTo(expectedGoalAccomplished));
		assertThat(d.getTotalMinutesBeyondGoal(goal), equalTo(expectedTotalBeyondGoal));
	}
}
