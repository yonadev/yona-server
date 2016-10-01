/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Duration;
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

import org.apache.commons.lang.ArrayUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.support.Repositories;

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.GoalService;
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
	private final Map<String, Goal> goalMap = new HashMap<String, Goal>();

	@Mock
	private UserService mockUserService;
	@Mock
	private GoalService mockGoalService;
	@Mock
	private YonaProperties mockYonaProperties;
	@Mock
	private UserAnonymizedService mockUserAnonymizedService;
	@Mock
	private WeekActivityRepository mockWeekActivityRepository;
	@Mock
	private DayActivityRepository mockDayActivityRepository;
	@Mock
	private Repositories mockRepositories;
	@Mock
	private AnalysisEngineProxyService analysisEngineProxyService;

	@InjectMocks
	private final ActivityService service = new ActivityService();

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
		RepositoryProvider.setRepositories(mockRepositories);
		when(mockRepositories.getRepositoryFor(DayActivity.class)).thenReturn(mockDayActivityRepository);

		// created 2 weeks ago
		gamblingGoal = BudgetGoal.createNoGoInstance(ZonedDateTime.now().minusWeeks(2),
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
						new HashSet<String>(Arrays.asList("poker", "lotto")), Collections.emptySet()));
		newsGoal = BudgetGoal.createNoGoInstance(ZonedDateTime.now(), ActivityCategory.createInstance(UUID.randomUUID(),
				usString("news"), false, new HashSet<String>(Arrays.asList("refdag", "bbc")), Collections.emptySet()));
		gamingGoal = BudgetGoal.createNoGoInstance(ZonedDateTime.now(), ActivityCategory.createInstance(UUID.randomUUID(),
				usString("gaming"), false, new HashSet<String>(Arrays.asList("games")), Collections.emptySet()));
		socialGoal = TimeZoneGoal.createInstance(ZonedDateTime.now(), ActivityCategory.createInstance(UUID.randomUUID(),
				usString("social"), false, new HashSet<String>(Arrays.asList("social")), Collections.emptySet()),
				Collections.emptyList());
		shoppingGoal = BudgetGoal.createInstance(ZonedDateTime.now(), ActivityCategory.createInstance(UUID.randomUUID(),
				usString("shopping"), false, new HashSet<String>(Arrays.asList("webshop")), Collections.emptySet()), 1);

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

		// Stub the GoalService to return our goals.
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, gamblingGoal.getID())).thenReturn(gamblingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, newsGoal.getID())).thenReturn(newsGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, gamingGoal.getID())).thenReturn(gamingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, socialGoal.getID())).thenReturn(socialGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, shoppingGoal.getID())).thenReturn(shoppingGoal);

		// save should not return null but the saved entity
		when(mockDayActivityRepository.save(any(DayActivity.class))).thenAnswer(new Answer<DayActivity>() {
			@Override
			public DayActivity answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (DayActivity) args[0];
			}
		});
		// save should not return null but the saved entity
		when(mockWeekActivityRepository.save(any(WeekActivity.class))).thenAnswer(new Answer<WeekActivity>() {
			@Override
			public WeekActivity answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (WeekActivity) args[0];
			}
		});
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	@Test
	public void dayActivityOverview()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));
		ZonedDateTime yesterday = today.minusDays(1);

		// gambling goal was created 2 weeks ago, see above
		// mock some activity on yesterday 20:58-21:00
		DayActivity yesterdayRecordedActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, yesterday);
		Activity recordedActivity = Activity.createInstance(yesterday.plusHours(20).plusMinutes(58),
				yesterday.plusHours(21).plusMinutes(00));
		yesterdayRecordedActivity.addActivity(recordedActivity);
		when(mockDayActivityRepository.findAllActivitiesForUserInIntervalEndIncluded(userAnonID, today.minusDays(2).toLocalDate(),
				today.toLocalDate())).thenReturn(Arrays.asList(yesterdayRecordedActivity));

		Page<DayActivityOverviewDTO<DayActivityDTO>> dayOverviews = service.getUserDayActivityOverviews(userID,
				new PageRequest(0, 3));

		// assert that the right retrieve from database was done
		verify(mockDayActivityRepository, times(1)).findAllActivitiesForUserInIntervalEndIncluded(userAnonID,
				today.minusDays(2).toLocalDate(), today.toLocalDate());

		// because the gambling goal was added with creation date two weeks ago, there are multiple days, equal to the limit of
		// our page request = 3
		assertThat(dayOverviews.getNumberOfElements(), equalTo(3));

		// get the current day (first item)
		DayActivityOverviewDTO<DayActivityDTO> dayOverview = dayOverviews.getContent().get(0);
		assertThat(dayOverview.getDayActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		DayActivityDTO dayActivityForGambling = dayOverview.getDayActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(dayActivityForGambling.getStartTime(), equalTo(today));
		assertThat(dayActivityForGambling.getTotalActivityDurationMinutes().get(), equalTo(0));
		assertThat(dayActivityForGambling.getTotalMinutesBeyondGoal(), equalTo(0));

		// get yesterday, with recorded activity
		dayOverview = dayOverviews.getContent().get(1);
		assertThat(dayOverview.getDayActivities().size(), equalTo(1));
		dayActivityForGambling = dayOverview.getDayActivities().stream().filter(a -> a.getGoalID().equals(gamblingGoal.getID()))
				.findAny().get();
		assertThat(dayActivityForGambling.getStartTime(), equalTo(yesterday));
		assertThat(dayActivityForGambling.getTotalActivityDurationMinutes().get(), equalTo(2));
		assertThat(dayActivityForGambling.getTotalMinutesBeyondGoal(), equalTo(2));
	}

	@Test
	public void weekActivityOverview()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));

		// gambling goal was created 2 weeks ago, see above
		// mock some activity in previous week on Saturday 19:10-19:55
		WeekActivity previousWeekRecordedActivity = WeekActivity.createInstance(userAnonEntity, gamblingGoal,
				getWeekStartTime(today.minusWeeks(1)));
		ZonedDateTime saturdayStartOfDay = getWeekStartTime(today).minusDays(1);
		DayActivity previousWeekSaturdayRecordedActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal,
				saturdayStartOfDay);
		Activity recordedActivity = Activity.createInstance(saturdayStartOfDay.plusHours(19).plusMinutes(10),
				saturdayStartOfDay.plusHours(19).plusMinutes(55));
		previousWeekSaturdayRecordedActivity.addActivity(recordedActivity);
		when(mockDayActivityRepository.findActivitiesForUserAndGoalsInIntervalEndExcluded(userAnonID,
				new HashSet<UUID>(Arrays.asList(gamblingGoal.getID())), getWeekStartTime(today.minusWeeks(1)).toLocalDate(),
				getWeekEndDate(getWeekStartTime(today.minusWeeks(1)).toLocalDate())))
						.thenReturn(Arrays.asList(
								DayActivity.createInstance(userAnonEntity, gamblingGoal, getWeekStartTime(today).minusDays(7)),
								DayActivity.createInstance(userAnonEntity, gamblingGoal, getWeekStartTime(today).minusDays(6)),
								DayActivity.createInstance(userAnonEntity, gamblingGoal, getWeekStartTime(today).minusDays(5)),
								DayActivity.createInstance(userAnonEntity, gamblingGoal, getWeekStartTime(today).minusDays(4)),
								DayActivity.createInstance(userAnonEntity, gamblingGoal, getWeekStartTime(today).minusDays(3)),
								DayActivity.createInstance(userAnonEntity, gamblingGoal, getWeekStartTime(today).minusDays(2)),
								previousWeekSaturdayRecordedActivity));
		when(mockWeekActivityRepository.findAll(userAnonID, getWeekStartTime(today.minusWeeks(4)).toLocalDate(),
				getWeekStartTime(today).toLocalDate()))
						.thenReturn(new HashSet<WeekActivity>(Arrays.asList(previousWeekRecordedActivity)));

		Page<WeekActivityOverviewDTO> weekOverviews = service.getUserWeekActivityOverviews(userID, new PageRequest(0, 5));

		// assert that the right retrieve from database was done
		verify(mockWeekActivityRepository, times(1)).findAll(userAnonID, getWeekStartTime(today.minusWeeks(4)).toLocalDate(),
				getWeekStartTime(today).toLocalDate());

		// because the gambling goal was added with creation date two weeks ago, there are multiple weeks
		assertThat(weekOverviews.getNumberOfElements(), equalTo(3));

		// get the current week (first item)
		WeekActivityOverviewDTO weekOverview = weekOverviews.getContent().get(0);
		assertThat(weekOverview.getWeekActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		WeekActivityDTO weekActivityForGambling = weekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(weekActivityForGambling.getStartTime(), equalTo(getWeekStartTime(today)));
		// TODO: mock day activity in this week?
		// int thisWeekNumberOfWeekDaysPast = today.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : today.getDayOfWeek().getValue();
		// assertThat(weekActivityForGambling.getDayActivities().size(), equalTo(1 + thisWeekNumberOfWeekDaysPast));
		//// always contains Sunday because it is the first day of the week
		// assertThat(weekActivityForGambling.getDayActivities(), hasKey(DayOfWeek.SUNDAY));

		// get the previous week, with recorded activity
		weekOverview = weekOverviews.getContent().get(1);
		assertThat(weekOverview.getWeekActivities().size(), equalTo(1));
		weekActivityForGambling = weekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(weekActivityForGambling.getStartTime(), equalTo(getWeekStartTime(today.minusWeeks(1))));
		assertThat(weekActivityForGambling.getDayActivities().size(), equalTo(7));
		DayActivityDTO previousWeekSaturdayActivity = weekActivityForGambling.getDayActivities().get(DayOfWeek.SATURDAY);
		assertThat(previousWeekSaturdayActivity.getTotalActivityDurationMinutes().get(), equalTo(45));
		assertThat(previousWeekSaturdayActivity.getTotalMinutesBeyondGoal(), equalTo(45));
		DayActivityDTO previousWeekFridayActivity = weekActivityForGambling.getDayActivities().get(DayOfWeek.FRIDAY);
		assertThat(previousWeekFridayActivity.getTotalActivityDurationMinutes().get(), equalTo(0));

		// get the week the gambling goal was created
		weekOverview = weekOverviews.getContent().get(2);
		assertThat(weekOverview.getWeekActivities().size(), equalTo(1));
		weekActivityForGambling = weekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(weekActivityForGambling.getStartTime(), equalTo(getWeekStartTime(today.minusWeeks(2))));
		// TODO: mock day activity in this week?
		// int expectedNumberOfWeekDaysRecorded = gamblingGoal.getCreationTime().getDayOfWeek() == DayOfWeek.SUNDAY ? 7
		// : 7 - gamblingGoal.getCreationTime().getDayOfWeek().getValue();
		// assertThat(weekActivityForGambling.getDayActivities().size(), equalTo(expectedNumberOfWeekDaysRecorded));
		//// always contains Saturday because it is the last day of the week
		// assertThat(weekActivityForGambling.getDayActivities(), hasKey(DayOfWeek.SATURDAY));
	}

	@Test
	public void dayActivityOverviewInactivity()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));

		Page<DayActivityOverviewDTO<DayActivityDTO>> inactivityDayOverviews = service.getUserDayActivityOverviews(userID,
				new PageRequest(0, 3));
		// because the gambling goal was added with creation date two weeks ago, there are multiple days
		assertThat(inactivityDayOverviews.getNumberOfElements(), equalTo(3));
		// the other goals were created today, so get the most recent (first) element
		DayActivityOverviewDTO<DayActivityDTO> inactivityDayOverview = inactivityDayOverviews.getContent().get(0);
		assertThat(inactivityDayOverview.getDayActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		DayActivityDTO inactivityDayForGambling = inactivityDayOverview.getDayActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(inactivityDayForGambling.getStartTime(), equalTo(today));
		assertThat(inactivityDayForGambling.getTotalActivityDurationMinutes().get(), equalTo(0));
		assertThat(inactivityDayForGambling.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void weekActivityOverviewInactivity()
	{
		Page<WeekActivityOverviewDTO> inactivityWeekOverviews = service.getUserWeekActivityOverviews(userID,
				new PageRequest(0, 5));
		// because the gambling goal was added with creation date two weeks ago, there are multiple weeks
		assertThat(inactivityWeekOverviews.getNumberOfElements(), equalTo(3));
		// the other goals were created today, so get the most recent (first) element
		WeekActivityOverviewDTO inactivityWeekOverview = inactivityWeekOverviews.getContent().get(0);
		assertThat(inactivityWeekOverview.getWeekActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		WeekActivityDTO inactivityWeekForGambling = inactivityWeekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalID().equals(gamblingGoal.getID())).findAny().get();
		assertThat(inactivityWeekForGambling.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		// TODO: mock day activity in this week?
		// ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));
		// int thisWeekNumberOfWeekDaysPast = today.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : today.getDayOfWeek().getValue();
		// assertThat(inactivityWeekForGambling.getDayActivities().size(), equalTo(1 + thisWeekNumberOfWeekDaysPast));
	}

	@Test
	public void dayActivityDetailInactivity()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));

		DayActivityDTO inactivityDay = service.getUserDayActivityDetail(userID, LocalDate.now(userAnonZone),
				gamblingGoal.getID());
		assertThat(inactivityDay.getSpread().size(), equalTo(96));
		assertThat(inactivityDay.getStartTime(), equalTo(today));
		assertThat(inactivityDay.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityDay.getTotalActivityDurationMinutes().get(), equalTo(0));
		assertThat(inactivityDay.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void weekActivityDetailInactivity()
	{
		WeekActivityDTO inactivityWeek = service.getUserWeekActivityDetail(userID, getWeekStartDate(LocalDate.now(userAnonZone)),
				gamblingGoal.getID());
		assertThat(inactivityWeek.getSpread().size(), equalTo(96));
		assertThat(inactivityWeek.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		assertThat(inactivityWeek.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityWeek.getTotalActivityDurationMinutes().get(), equalTo(0));
	}

	@Test
	@Ignore
	public void spreadShortDurationInMiddleOfCell()
	{
		int hour = 20;
		Duration activityStartTime = Duration.ofHours(hour).plusMinutes(5).plusSeconds(8);
		Duration activityDuration = Duration.ofSeconds(3);
		int[] expectedSpread = new int[96];
		Arrays.fill(expectedSpread, 0);
		expectedSpread[hour * 4] = 1;
		assertSpread(activityStartTime, activityDuration, expectedSpread);
	}

	@Test
	@Ignore
	public void spreadShortDurationInNextCell()
	{
		int hour = 20;
		Duration activityStartTime = Duration.ofHours(hour).plusMinutes(5).plusSeconds(8);
		Duration activityDuration = Duration.ofSeconds(55).plusMinutes(9);
		int[] expectedSpread = new int[96];
		Arrays.fill(expectedSpread, 0);
		expectedSpread[hour * 4] = 10;
		expectedSpread[hour * 4 + 1] = 1;
		assertSpread(activityStartTime, activityDuration, expectedSpread);
	}

	@Test
	public void spreadShortDurationInPreviousCell()
	{
		int hour = 20;
		Duration activityStartTime = Duration.ofHours(hour).plusMinutes(14).plusSeconds(57);
		Duration activityDuration = Duration.ofSeconds(3).plusMinutes(6);
		int[] expectedSpread = new int[96];
		Arrays.fill(expectedSpread, 0);
		expectedSpread[hour * 4] = 1;
		expectedSpread[hour * 4 + 1] = 6;
		assertSpread(activityStartTime, activityDuration, expectedSpread);
	}

	private void assertSpread(Duration activityStartTime, Duration activityDuration, int[] expectedSpread)
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));
		ZonedDateTime yesterday = today.minusDays(1);

		// gambling goal was created 2 weeks ago, see above
		// mock some activity on yesterday 20:58-21:00
		DayActivity yesterdayRecordedActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, yesterday);
		Activity recordedActivity = Activity.createInstance(yesterday.plus(activityStartTime),
				yesterday.plus(activityStartTime).plus(activityDuration));
		yesterdayRecordedActivity.addActivity(recordedActivity);
		when(mockDayActivityRepository.findOne(userAnonID, yesterday.toLocalDate(), gamblingGoal.getID()))
				.thenReturn(yesterdayRecordedActivity);

		DayActivityDTO inactivityDay = service.getUserDayActivityDetail(userID, yesterday.toLocalDate(), gamblingGoal.getID());
		verify(mockDayActivityRepository, times(1)).findOne(userAnonID, yesterday.toLocalDate(), gamblingGoal.getID());
		assertThat(inactivityDay.getSpread(), equalTo(Arrays.asList(ArrayUtils.toObject((expectedSpread)))));
	}

	private ZonedDateTime getWeekStartTime(ZonedDateTime dateTime)
	{
		ZonedDateTime dateAtStartOfDay = getDayStartTime(dateTime);
		switch (dateAtStartOfDay.getDayOfWeek())
		{
			case SUNDAY:
				return dateAtStartOfDay;
			default:
				return dateAtStartOfDay.minusDays(dateAtStartOfDay.getDayOfWeek().getValue());
		}
	}

	private ZonedDateTime getDayStartTime(ZonedDateTime dateTime)
	{
		return dateTime.truncatedTo(ChronoUnit.DAYS);
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

	private LocalDate getWeekEndDate(LocalDate date)
	{
		return getWeekStartDate(date).plusDays(7);
	}
}