/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.util.TimeUtil;

public abstract class IntervalActivityTestsBase
{
	protected ZoneId testZone;

	protected BudgetGoal budgetGoal;
	protected TimeZoneGoal timeZoneGoal;

	protected UserAnonymized userAnonEntity;

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

	protected ZonedDateTime getDate(DayActivity d, String timeString)
	{
		int hourIndex = timeString.indexOf(':');
		int hour = Integer.parseInt(timeString.substring(0, hourIndex));
		int minuteIndex = timeString.indexOf(':', hourIndex + 1);
		if (minuteIndex == -1)
		{
			int minute = Integer.parseInt(timeString.substring(hourIndex + 1));
			return d.getStartTime().withHour(hour).withMinute(minute);
		}
		else
		{
			int minute = Integer.parseInt(timeString.substring(hourIndex + 1, minuteIndex));
			int second = Integer.parseInt(timeString.substring(minuteIndex + 1));
			return d.getStartTime().withHour(hour).withMinute(minute).withSecond(second);
		}
	}

	protected void addActivity(DayActivity d, String startTimeString, String endTimeString)
	{
		d.addActivity(Activity.createInstance(testZone, getDate(d, startTimeString).toLocalDateTime(),
				getDate(d, endTimeString).toLocalDateTime()));
	}

	protected void assertSpreadItemsAndTotal(IntervalActivity d, String expectedSpreadItems,
			int expectedTotalActivityDurationMinutes)
	{
		assertThat(d.areAggregatesComputed(), equalTo(false));
		assertSpreadItems(d.getSpread(), expectedSpreadItems);
		assertThat(d.getTotalActivityDurationMinutes(), equalTo(expectedTotalActivityDurationMinutes));

		d.computeAggregates();
		assertThat(d.areAggregatesComputed(), equalTo(true));
		assertSpreadItems(d.getSpread(), expectedSpreadItems);
		assertThat(d.getTotalActivityDurationMinutes(), equalTo(expectedTotalActivityDurationMinutes));
	}

	protected void assertSpreadItems(List<Integer> actualSpread, String expectedSpreadItems)
	{
		Arrays.stream(expectedSpreadItems.split(","))
				.forEach(expectedSpreadItem -> assertSpreadItem(actualSpread, expectedSpreadItem));
	}

	protected void assertSpreadItem(List<Integer> actualSpread, String expectedSpreadItem)
	{
		int separatorIndex = expectedSpreadItem.indexOf('=');
		int spreadIndex = Integer.parseInt(expectedSpreadItem.substring(0, separatorIndex));
		int expectedValue = Integer.parseInt(expectedSpreadItem.substring(separatorIndex + 1));
		assertThat(MessageFormat.format("expected value {0} at spread index {1}", expectedValue, spreadIndex),
				actualSpread.get(spreadIndex), equalTo(expectedValue));
	}
}