/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;

import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.util.TimeUtil;

public abstract class IntervalActivityTestBase
{
	protected ZoneId testZone;

	protected BudgetGoal budgetGoal;
	protected TimeZoneGoal timeZoneGoal;

	protected UserAnonymized userAnonEntity;
	protected DeviceAnonymized deviceAnonEntity;

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
		Set<Goal> goals = new HashSet<>(Arrays.asList(budgetGoal));
		deviceAnonEntity = DeviceAnonymized.createInstance(0, OperatingSystem.ANDROID, "Unknown");
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		userAnonEntity.addDeviceAnonymized(deviceAnonEntity);
	}

	protected ZonedDateTime getTimeOnDay(DayActivity dayActivity, String timeString)
	{
		TemporalAccessor time = DateTimeFormatter.ISO_LOCAL_TIME.parse(timeString);
		return dayActivity.getStartTime().withHour(time.get(ChronoField.HOUR_OF_DAY))
				.withMinute(time.get(ChronoField.MINUTE_OF_HOUR)).withSecond(time.get(ChronoField.SECOND_OF_MINUTE));
	}

	protected void addActivity(DayActivity dayActivity, String startTimeString, String endTimeString)
	{
		dayActivity.addActivity(
				Activity.createInstance(deviceAnonEntity, testZone, getTimeOnDay(dayActivity, startTimeString).toLocalDateTime(),
						getTimeOnDay(dayActivity, endTimeString).toLocalDateTime(), Optional.empty()));
	}

	protected void assertSpreadItemsAndTotal(IntervalActivity intervalActivity, String expectedSpreadItems,
			int expectedTotalActivityDurationMinutes)
	{
		assertThat(intervalActivity.areAggregatesComputed(), equalTo(false));
		assertSpreadItems(intervalActivity.getSpread(), expectedSpreadItems);
		assertThat(intervalActivity.getTotalActivityDurationMinutes(), equalTo(expectedTotalActivityDurationMinutes));

		intervalActivity.computeAggregates();
		assertSpreadItems(intervalActivity.getSpread(), expectedSpreadItems);
		assertThat(intervalActivity.getTotalActivityDurationMinutes(), equalTo(expectedTotalActivityDurationMinutes));
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