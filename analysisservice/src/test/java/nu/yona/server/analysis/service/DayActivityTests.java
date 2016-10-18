/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;

public class DayActivityTests
{
	private ZoneId testZone;

	private BudgetGoal goal;

	private UserAnonymized userAnonEntity;

	@Before
	public void setUp()
	{
		testZone = ZoneId.of("Europe/Amsterdam");
		ActivityCategory activityCategory = ActivityCategory.createInstance(UUID.randomUUID(),
				Collections.singletonMap(Locale.US, "being bored"), false, Collections.emptySet(), Collections.emptySet(),
				Collections.singletonMap(Locale.US, "Descr"));
		goal = BudgetGoal.createInstance(ZonedDateTime.now(), activityCategory, 60);
		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(goal));
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
	}

	private ZonedDateTime getZonedDateTime(int hour, int minute, int second)
	{
		return ZonedDateTime.of(2016, 3, 17, hour, minute, second, 0, testZone);
	}

	private DayActivity createDayActivity()
	{
		return DayActivity.createInstance(userAnonEntity, goal, getZonedDateTime(0, 0, 0));
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
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(19, 59)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(4));
		assertThat(d.getSpread().get(80), equalTo(0));
	}

	@Test
	public void testSpreadSpan2()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(20, 1)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(5));
		assertThat(d.getSpread().get(80), equalTo(1));
		assertThat(d.getSpread().get(81), equalTo(0));
	}

	@Test
	public void testSpreadSpan2StartEdge()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 45), getDate(20, 1)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(15));
		assertThat(d.getSpread().get(80), equalTo(1));
		assertThat(d.getSpread().get(81), equalTo(0));
	}

	@Test
	public void testSpreadSpan2EndEdge()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(20, 15)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(5));
		assertThat(d.getSpread().get(80), equalTo(15));
		assertThat(d.getSpread().get(81), equalTo(0));
	}

	@Test
	public void testSpreadSpan1StartEndEdge()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 45, 00), getDate(19, 59, 59)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(14));
		assertThat(d.getSpread().get(80), equalTo(0));
	}

	@Test
	public void testSpreadSpan3()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 55), getDate(20, 16)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(5));
		assertThat(d.getSpread().get(80), equalTo(15));
		assertThat(d.getSpread().get(81), equalTo(1));
		assertThat(d.getSpread().get(82), equalTo(0));
	}

	@Test
	public void testSpreadMultipleActivitiesOverlappingUnsorted()
	{
		DayActivity d = createDayActivity();
		d.addActivity(Activity.createInstance(getDate(19, 48), getDate(19, 50)));
		d.addActivity(Activity.createInstance(getDate(19, 46), getDate(19, 59)));
		d.addActivity(Activity.createInstance(getDate(20, 1), getDate(20, 17)));
		assertThat(d.getSpread().get(78), equalTo(0));
		assertThat(d.getSpread().get(79), equalTo(13));
		assertThat(d.getSpread().get(80), equalTo(14));
		assertThat(d.getSpread().get(81), equalTo(2));
		assertThat(d.getSpread().get(82), equalTo(0));
	}
}
