/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.util.TimeUtil;

public class DayActivityTests
{
	private ZoneId testZone;

	private BudgetGoal budgetGoal;
	private TimeZoneGoal timeZoneGoal;

	private UserAnonymized userAnonEntity;

	@Before
	public void setUp()
	{
		testZone = ZoneId.of("Europe/Amsterdam");
		ActivityCategory activityCategory = ActivityCategory.createInstance(UUID.randomUUID(),
				Collections.singletonMap(Locale.US, "being bored"), false, Collections.emptySet(), Collections.emptySet(),
				Collections.singletonMap(Locale.US, "Descr"));
		budgetGoal = BudgetGoal.createInstance(TimeUtil.utcNow(), activityCategory, 60);
		timeZoneGoal = TimeZoneGoal.createInstance(TimeUtil.utcNow(), activityCategory,
				Arrays.asList("05:00-12:30", "13:00-24:00"));
		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(budgetGoal));
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
	}

	private ZonedDateTime getZonedDateTime(int hour, int minute, int second)
	{
		return ZonedDateTime.of(2016, 3, 17, hour, minute, second, 0, testZone);
	}

	private DayActivity createDayActivity()
	{
		return DayActivity.createInstance(userAnonEntity, budgetGoal, testZone, getZonedDateTime(0, 0, 0).toLocalDate());
	}

	private DayActivity createDayActivityTimeZoneGoal()
	{
		return DayActivity.createInstance(userAnonEntity, timeZoneGoal, testZone, getZonedDateTime(0, 0, 0).toLocalDate());
	}

	private ZonedDateTime getDate(String timeString)
	{
		int hourIndex = timeString.indexOf(':');
		int hour = Integer.parseInt(timeString.substring(0, hourIndex));
		int minuteIndex = timeString.indexOf(':', hourIndex + 1);
		if (minuteIndex == -1)
		{
			int minute = Integer.parseInt(timeString.substring(hourIndex + 1));
			return getDate(hour, minute);
		}
		else
		{
			int minute = Integer.parseInt(timeString.substring(hourIndex + 1, minuteIndex));
			int second = Integer.parseInt(timeString.substring(minuteIndex + 1));
			return getDate(hour, minute, second);
		}
	}

	private ZonedDateTime getDate(int hour, int minute)
	{
		return getDate(hour, minute, 0);
	}

	private ZonedDateTime getDate(int hour, int minute, int second)
	{
		return getZonedDateTime(hour, minute, second);
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

	private void addActivity(DayActivity d, String startTimeString, String endTimeString)
	{
		d.addActivity(Activity.createInstance(testZone, getDate(startTimeString).toLocalDateTime(),
				getDate(endTimeString).toLocalDateTime()));
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

	private void assertSpreadItemsAndTotal(DayActivity d, String expectedSpreadItems, int expectedTotalActivityDurationMinutes)
	{
		assertThat(d.areAggregatesComputed(), equalTo(false));
		assertSpreadItems(d.getSpread(), expectedSpreadItems);
		assertThat(d.getTotalActivityDurationMinutes(), equalTo(expectedTotalActivityDurationMinutes));

		d.computeAggregates();
		assertThat(d.areAggregatesComputed(), equalTo(true));
		assertSpreadItems(d.getSpread(), expectedSpreadItems);
		assertThat(d.getTotalActivityDurationMinutes(), equalTo(expectedTotalActivityDurationMinutes));
	}

	private void assertSpreadItems(List<Integer> actualSpread, String expectedSpreadItems)
	{
		Arrays.stream(expectedSpreadItems.split(","))
				.forEach(expectedSpreadItem -> assertSpreadItem(actualSpread, expectedSpreadItem));
	}

	private void assertSpreadItem(List<Integer> actualSpread, String expectedSpreadItem)
	{
		int separatorIndex = expectedSpreadItem.indexOf('=');
		int spreadIndex = Integer.parseInt(expectedSpreadItem.substring(0, separatorIndex));
		int expectedValue = Integer.parseInt(expectedSpreadItem.substring(separatorIndex + 1));
		assertThat(MessageFormat.format("expected value {0} at spread index {1}", expectedValue, spreadIndex),
				actualSpread.get(spreadIndex), equalTo(expectedValue));
	}
}
