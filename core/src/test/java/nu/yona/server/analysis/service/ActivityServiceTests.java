/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.properties.AnalysisServiceProperties;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;

@RunWith(MockitoJUnitRunner.class)
public class ActivityServiceTests
{
	private Map<String, Goal> goalMap = new HashMap<String, Goal>();

	@Mock
	private UserService mockUserService;
	@Mock
	private YonaProperties mockYonaProperties;
	@Mock
	private UserAnonymizedService mockUserAnonymizedService;
	@Mock
	private WeekActivityRepository mockWeekActivityRepository;
	@Mock
	private DayActivityRepository mockDayActivityRepository;
	@InjectMocks
	private ActivityService service = new ActivityService();

	private Goal gamblingGoal;
	private Goal newsGoal;
	private Goal gamingGoal;
	private Goal socialGoal;
	private Goal shoppingGoal;
	private UUID userID;
	private UUID userAnonID;
	private UserAnonymized userAnonEntity;
	private ZoneId userAnonZone;

	@Before
	public void setUp()
	{
		gamblingGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance("gambling", false,
				new HashSet<String>(Arrays.asList("poker", "lotto")), Collections.emptySet()));
		newsGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance("news", false,
				new HashSet<String>(Arrays.asList("refdag", "bbc")), Collections.emptySet()));
		gamingGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance("gaming", false,
				new HashSet<String>(Arrays.asList("games")), Collections.emptySet()));
		socialGoal = TimeZoneGoal.createInstance(ActivityCategory.createInstance("social", false,
				new HashSet<String>(Arrays.asList("social")), Collections.emptySet()), new String[0]);
		shoppingGoal = BudgetGoal.createInstance(ActivityCategory.createInstance("shopping", false,
				new HashSet<String>(Arrays.asList("webshop")), Collections.emptySet()), 1);

		goalMap.put("gambling", gamblingGoal);
		goalMap.put("news", newsGoal);
		goalMap.put("gaming", gamingGoal);
		goalMap.put("social", socialGoal);
		goalMap.put("shopping", shoppingGoal);

		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal));
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		UserAnonymizedDTO userAnon = UserAnonymizedDTO.createInstance(userAnonEntity);
		userAnonZone = ZoneId.of(userAnon.getTimeZoneId());
		userAnonID = userAnon.getID();

		userID = UUID.randomUUID();

		// Stub the UserService to return our user anonymized ID.
		when(mockUserService.getUserAnonymizedID(userID)).thenReturn(userAnonID);

		// Stub the UserAnonymizedService to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonID)).thenReturn(userAnon);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonID)).thenReturn(userAnonEntity);
	}

	@Test
	public void dayActivityOverviewZero()
	{
		Page<DayActivityOverviewDTO> zeroActivityDayOverviews = service.getDayActivityOverviews(userID, new PageRequest(0, 3));
		// because the goals were added this day, previous days are left out
		assertThat(zeroActivityDayOverviews.getNumberOfElements(), equalTo(1));
		DayActivityOverviewDTO zeroActivityDayOverview = zeroActivityDayOverviews.getContent().get(0);
		assertThat(zeroActivityDayOverview.getDayActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		DayActivityDTO zeroActivityDayForGambling = zeroActivityDayOverview.getDayActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(zeroActivityDayForGambling.getStartTime(),
				equalTo(ZonedDateTime.now(userAnonZone).truncatedTo(ChronoUnit.DAYS)));
		assertThat(zeroActivityDayForGambling.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(zeroActivityDayForGambling.getTotalActivityDurationMinutes(), equalTo(0));
		assertThat(zeroActivityDayForGambling.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void weekActivityOverviewZero()
	{
		Page<WeekActivityOverviewDTO> zeroActivityWeekOverviews = service.getWeekActivityOverviews(userID, new PageRequest(0, 5));
		// because the goals were added this week, previous weeks are left out
		assertThat(zeroActivityWeekOverviews.getNumberOfElements(), equalTo(1));
		WeekActivityOverviewDTO zeroActivityWeekOverview = zeroActivityWeekOverviews.getContent().get(0);
		assertThat(zeroActivityWeekOverview.getWeekActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		WeekActivityDTO zeroActivityWeekForGambling = zeroActivityWeekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(zeroActivityWeekForGambling.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		assertThat(zeroActivityWeekForGambling.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(zeroActivityWeekForGambling.getDayActivities().size(), equalTo(0));
	}

	@Test
	public void dayActivityDetailZero()
	{
		DayActivityDTO zeroActivityDay = service.getDayActivityDetail(userID, LocalDate.now(userAnonZone), gamblingGoal.getID());
		assertThat(zeroActivityDay.getSpread().size(), equalTo(96));
		assertThat(zeroActivityDay.getStartTime(), equalTo(ZonedDateTime.now(userAnonZone).truncatedTo(ChronoUnit.DAYS)));
		assertThat(zeroActivityDay.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(zeroActivityDay.getTotalActivityDurationMinutes(), equalTo(0));
		assertThat(zeroActivityDay.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void weekActivityDetailZero()
	{
		WeekActivityDTO zeroActivityWeek = service.getWeekActivityDetail(userID, getWeekStartDate(LocalDate.now(userAnonZone)),
				gamblingGoal.getID());
		assertThat(zeroActivityWeek.getSpread().size(), equalTo(96));
		assertThat(zeroActivityWeek.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		assertThat(zeroActivityWeek.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(zeroActivityWeek.getTotalActivityDurationMinutes(), equalTo(0));
	}

	private ZonedDateTime getWeekStartTime(ZonedDateTime dateTime)
	{
		ZonedDateTime dateAtStartOfDay = dateTime.truncatedTo(ChronoUnit.DAYS);
		switch (dateAtStartOfDay.getDayOfWeek())
		{
			case SUNDAY:
				return dateAtStartOfDay;
			default:
				return dateAtStartOfDay.minusDays(dateAtStartOfDay.getDayOfWeek().getValue());
		}
	}

	private LocalDate getWeekStartDate(LocalDate date)
	{
		switch (date.getDayOfWeek())
		{
			case SUNDAY:
				return date;
			default:
				return date.minusDays(date.getDayOfWeek().getValue());
		}
	}
}