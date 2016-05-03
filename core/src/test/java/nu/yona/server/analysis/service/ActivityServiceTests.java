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
import java.util.Locale;
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
import nu.yona.server.analysis.entities.WeekActivity;
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
		gamblingGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"),
				false, new HashSet<String>(Arrays.asList("poker", "lotto")), Collections.emptySet()));
		// created 1 week ago
		gamblingGoal.setCreationTime(ZonedDateTime.now().minusWeeks(1));
		newsGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance(UUID.randomUUID(), usString("news"), false,
				new HashSet<String>(Arrays.asList("refdag", "bbc")), Collections.emptySet()));
		gamingGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance(UUID.randomUUID(), usString("gaming"), false,
				new HashSet<String>(Arrays.asList("games")), Collections.emptySet()));
		socialGoal = TimeZoneGoal.createInstance(ActivityCategory.createInstance(UUID.randomUUID(), usString("social"), false,
				new HashSet<String>(Arrays.asList("social")), Collections.emptySet()), new String[0]);
		shoppingGoal = BudgetGoal.createInstance(ActivityCategory.createInstance(UUID.randomUUID(), usString("shopping"), false,
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

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Locale.forLanguageTag("en-US"), string);
	}

	@Test
	public void weekActivityOverview()
	{
		ZonedDateTime today = ZonedDateTime.now(userAnonZone);
		ZonedDateTime yesterday = today.minusDays(1);
		// gambling goal was created 1 week ago, see above
		when(mockWeekActivityRepository.findAll(userAnonID, LocalDate.from(today.toInstant()),
				LocalDate.from(today.minusWeeks(2).toInstant())))
						.thenReturn(new HashSet<WeekActivity>(
								Arrays.asList(WeekActivity.createInstance(userAnonEntity, gamblingGoal, yesterday))));

		Page<WeekActivityOverviewDTO> weekOverviews = service.getWeekActivityOverviews(userID, new PageRequest(0, 5));
		// because the gambling goal was added with creation date a week ago, there are multiple weeks
		assertThat(weekOverviews.getNumberOfElements(), equalTo(2));
		// get the week at which the gambling goal was created
		WeekActivityOverviewDTO inactivityWeekOverview = weekOverviews.getContent().get(0);
		assertThat(inactivityWeekOverview.getWeekActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		WeekActivityDTO inactivityWeekForGambling = inactivityWeekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(inactivityWeekForGambling.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		assertThat(inactivityWeekForGambling.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityWeekForGambling.getDayActivities().size(), equalTo(1));
	}

	@Test
	public void dayActivityOverviewInactivity()
	{
		Page<DayActivityOverviewDTO> inactivityDayOverviews = service.getDayActivityOverviews(userID, new PageRequest(0, 3));
		// because the gambling goal was added with creation date a week ago, there are multiple days
		assertThat(inactivityDayOverviews.getNumberOfElements(), equalTo(3));
		// the other goals were created today, so get the last element
		DayActivityOverviewDTO inactivityDayOverview = inactivityDayOverviews.getContent().get(2);
		assertThat(inactivityDayOverview.getDayActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		DayActivityDTO inactivityDayForGambling = inactivityDayOverview.getDayActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(inactivityDayForGambling.getStartTime(),
				equalTo(ZonedDateTime.now(userAnonZone).truncatedTo(ChronoUnit.DAYS)));
		assertThat(inactivityDayForGambling.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityDayForGambling.getTotalActivityDurationMinutes(), equalTo(0));
		assertThat(inactivityDayForGambling.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void weekActivityOverviewInactivity()
	{
		Page<WeekActivityOverviewDTO> inactivityWeekOverviews = service.getWeekActivityOverviews(userID, new PageRequest(0, 5));
		// because the gambling goal was added with creation date a week ago, there are multiple weeks
		assertThat(inactivityWeekOverviews.getNumberOfElements(), equalTo(2));
		// the other goals were created today, so get the last element
		WeekActivityOverviewDTO inactivityWeekOverview = inactivityWeekOverviews.getContent().get(1);
		assertThat(inactivityWeekOverview.getWeekActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		WeekActivityDTO inactivityWeekForGambling = inactivityWeekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(inactivityWeekForGambling.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		assertThat(inactivityWeekForGambling.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityWeekForGambling.getDayActivities().size(), equalTo(1));
	}

	@Test
	public void dayActivityDetailInactivity()
	{
		DayActivityDTO inactivityDay = service.getDayActivityDetail(userID, LocalDate.now(userAnonZone), gamblingGoal.getID());
		assertThat(inactivityDay.getSpread().size(), equalTo(96));
		assertThat(inactivityDay.getStartTime(), equalTo(ZonedDateTime.now(userAnonZone).truncatedTo(ChronoUnit.DAYS)));
		assertThat(inactivityDay.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityDay.getTotalActivityDurationMinutes(), equalTo(0));
		assertThat(inactivityDay.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void weekActivityDetailInactivity()
	{
		WeekActivityDTO inactivityWeek = service.getWeekActivityDetail(userID, getWeekStartDate(LocalDate.now(userAnonZone)),
				gamblingGoal.getID());
		assertThat(inactivityWeek.getSpread().size(), equalTo(96));
		assertThat(inactivityWeek.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		assertThat(inactivityWeek.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityWeek.getTotalActivityDurationMinutes(), equalTo(0));
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