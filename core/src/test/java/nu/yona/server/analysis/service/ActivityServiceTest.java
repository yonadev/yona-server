/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.support.Repositories;

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalRepository;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.properties.AnalysisServiceProperties;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.TimeUtil;

@RunWith(MockitoJUnitRunner.class)
public class ActivityServiceTest
{
	private final Map<String, Goal> goalMap = new HashMap<>();

	@Mock
	private UserService mockUserService;
	@Mock
	private GoalService mockGoalService;
	@Mock
	private YonaProperties mockYonaProperties;
	@Mock
	private UserAnonymizedService mockUserAnonymizedService;
	@Mock
	private GoalRepository mockGoalRepository;
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
	private UUID userId;
	private UUID userAnonId;
	private UserAnonymized userAnonEntity;
	private ZoneId userAnonZone;
	private DeviceAnonymized deviceAnonEntity;

	@Before
	public void setUp()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(DayActivity.class, mockDayActivityRepository);
		repositoriesMap.put(Goal.class, mockGoalRepository);
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);

		// created 2 weeks ago
		gamblingGoal = BudgetGoal.createNoGoInstance(TimeUtil.utcNow().minusWeeks(2),
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
						new HashSet<>(Arrays.asList("poker", "lotto")), Collections.emptySet(), usString("Descr")));
		newsGoal = BudgetGoal.createNoGoInstance(TimeUtil.utcNow(),
				ActivityCategory.createInstance(UUID.randomUUID(), usString("news"), false,
						new HashSet<>(Arrays.asList("refdag", "bbc")), Collections.emptySet(), usString("Descr")));
		gamingGoal = BudgetGoal.createNoGoInstance(TimeUtil.utcNow(), ActivityCategory.createInstance(UUID.randomUUID(),
				usString("gaming"), false, new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr")));
		socialGoal = TimeZoneGoal.createInstance(TimeUtil.utcNow(),
				ActivityCategory.createInstance(UUID.randomUUID(), usString("social"), false,
						new HashSet<>(Arrays.asList("social")), Collections.emptySet(), usString("Descr")),
				Collections.emptyList());
		shoppingGoal = BudgetGoal.createInstance(TimeUtil.utcNow(), ActivityCategory.createInstance(UUID.randomUUID(),
				usString("shopping"), false, new HashSet<>(Arrays.asList("webshop")), Collections.emptySet(), usString("Descr")),
				1);

		goalMap.put("gambling", gamblingGoal);
		goalMap.put("news", newsGoal);
		goalMap.put("gaming", gamingGoal);
		goalMap.put("social", socialGoal);
		goalMap.put("shopping", shoppingGoal);

		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<>(Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal));
		deviceAnonEntity = DeviceAnonymized.createInstance(0, OperatingSystem.ANDROID, "Unknown");
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		userAnonEntity.addDeviceAnonymized(deviceAnonEntity);
		UserAnonymizedDto userAnon = UserAnonymizedDto.createInstance(userAnonEntity);
		userAnonZone = userAnon.getTimeZone();
		userAnonId = userAnon.getId();

		userId = UUID.randomUUID();

		// Stub the UserService to return our user anonymized ID.
		when(mockUserService.getUserAnonymizedId(userId)).thenReturn(userAnonId);

		// Stub the UserAnonymizedService to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonId)).thenReturn(userAnon);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonId)).thenReturn(userAnonEntity);

		// Stub the GoalService to return our goals.
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamblingGoal.getId())).thenReturn(gamblingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, newsGoal.getId())).thenReturn(newsGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamingGoal.getId())).thenReturn(gamingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, socialGoal.getId())).thenReturn(socialGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, shoppingGoal.getId())).thenReturn(shoppingGoal);
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	@Test
	public void getUserDayActivityOverviews_activityPresent_resultsWithActivity()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));
		ZonedDateTime yesterday = today.minusDays(1);

		// gambling goal was created 2 weeks ago, see above
		// mock some activity on yesterday 20:58-21:00
		DayActivity yesterdayRecordedActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				yesterday.toLocalDate());
		Activity recordedActivity = Activity.createInstance(deviceAnonEntity, userAnonZone,
				yesterday.plusHours(20).plusMinutes(58).toLocalDateTime(),
				yesterday.plusHours(21).plusMinutes(00).toLocalDateTime(), Optional.empty());
		yesterdayRecordedActivity.addActivity(recordedActivity);
		Set<UUID> relevantGoalIds = userAnonEntity.getGoals().stream().map(Goal::getId).collect(Collectors.toSet());
		when(mockDayActivityRepository.findAll(userAnonId, relevantGoalIds, today.minusDays(2).toLocalDate(),
				today.plusDays(1).toLocalDate())).thenReturn(Arrays.asList(yesterdayRecordedActivity));

		Page<DayActivityOverviewDto<DayActivityDto>> dayOverviews = service.getUserDayActivityOverviews(userId,
				new PageRequest(0, 3));

		// assert that the right retrieve from database was done
		verify(mockDayActivityRepository, times(1)).findAll(userAnonId, relevantGoalIds, today.minusDays(2).toLocalDate(),
				today.plusDays(1).toLocalDate());

		// because the gambling goal was added with creation date two weeks ago, there are multiple days, equal to the limit of
		// our page request = 3
		assertThat(dayOverviews.getNumberOfElements(), equalTo(3));

		// get the current day (first item)
		DayActivityOverviewDto<DayActivityDto> dayOverview = dayOverviews.getContent().get(0);
		assertThat(dayOverview.getDayActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		DayActivityDto dayActivityForGambling = dayOverview.getDayActivities().stream()
				.filter(a -> a.getGoalId().equals(gamblingGoal.getId())).findAny().get();
		assertThat(dayActivityForGambling.getStartTime(), equalTo(today));
		assertThat(dayActivityForGambling.getTotalActivityDurationMinutes().get(), equalTo(0));
		assertThat(dayActivityForGambling.getTotalMinutesBeyondGoal(), equalTo(0));

		// get yesterday, with recorded activity
		dayOverview = dayOverviews.getContent().get(1);
		assertThat(dayOverview.getDayActivities().size(), equalTo(1));
		dayActivityForGambling = dayOverview.getDayActivities().stream().filter(a -> a.getGoalId().equals(gamblingGoal.getId()))
				.findAny().get();
		assertThat(dayActivityForGambling.getStartTime(), equalTo(yesterday));
		assertThat(dayActivityForGambling.getTotalActivityDurationMinutes().get(), equalTo(2));
		assertThat(dayActivityForGambling.getTotalMinutesBeyondGoal(), equalTo(2));
	}

	@Test
	public void getUserWeekActivityOverviews_activityPresent_resultsWithActivity()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));

		// gambling goal was created 2 weeks ago, see above
		// mock some activity in previous week on Saturday 19:10-19:55
		WeekActivity previousWeekRecordedActivity = WeekActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				getWeekStartTime(today.minusWeeks(1)).toLocalDate());
		ZonedDateTime saturdayStartOfDay = getWeekStartTime(today).minusDays(1);
		DayActivity previousWeekSaturdayRecordedActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				saturdayStartOfDay.toLocalDate());
		Activity recordedActivity = Activity.createInstance(deviceAnonEntity, userAnonZone,
				saturdayStartOfDay.plusHours(19).plusMinutes(10).toLocalDateTime(),
				saturdayStartOfDay.plusHours(19).plusMinutes(55).toLocalDateTime(), Optional.empty());
		previousWeekSaturdayRecordedActivity.addActivity(recordedActivity);
		previousWeekRecordedActivity.addDayActivity(DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				getWeekStartTime(today).minusDays(7).toLocalDate()));
		previousWeekRecordedActivity.addDayActivity(DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				getWeekStartTime(today).minusDays(6).toLocalDate()));
		previousWeekRecordedActivity.addDayActivity(DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				getWeekStartTime(today).minusDays(5).toLocalDate()));
		previousWeekRecordedActivity.addDayActivity(DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				getWeekStartTime(today).minusDays(4).toLocalDate()));
		previousWeekRecordedActivity.addDayActivity(DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				getWeekStartTime(today).minusDays(3).toLocalDate()));
		previousWeekRecordedActivity.addDayActivity(DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				getWeekStartTime(today).minusDays(2).toLocalDate()));
		previousWeekRecordedActivity.addDayActivity(previousWeekSaturdayRecordedActivity);

		when(mockWeekActivityRepository.findAll(userAnonId, getWeekStartTime(today.minusWeeks(4)).toLocalDate(),
				getWeekStartTime(today).plusWeeks(1).toLocalDate()))
						.thenReturn(new HashSet<>(Arrays.asList(previousWeekRecordedActivity)));

		Page<WeekActivityOverviewDto> weekOverviews = service.getUserWeekActivityOverviews(userId, new PageRequest(0, 5));

		// assert that the right retrieve from database was done
		verify(mockWeekActivityRepository, times(1)).findAll(userAnonId, getWeekStartTime(today.minusWeeks(4)).toLocalDate(),
				getWeekStartTime(today).plusWeeks(1).toLocalDate());

		// because the gambling goal was added with creation date two weeks ago, there are multiple weeks
		assertThat(weekOverviews.getNumberOfElements(), equalTo(3));

		// get the current week (first item)
		WeekActivityOverviewDto weekOverview = weekOverviews.getContent().get(0);
		assertThat(weekOverview.getWeekActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		WeekActivityDto weekActivityForGambling = weekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalId().equals(gamblingGoal.getId())).findAny().get();
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
				.filter(a -> a.getGoalId().equals(gamblingGoal.getId())).findAny().get();
		assertThat(weekActivityForGambling.getStartTime(), equalTo(getWeekStartTime(today.minusWeeks(1))));
		assertThat(weekActivityForGambling.getDayActivities().size(), equalTo(7));
		DayActivityDto previousWeekSaturdayActivity = weekActivityForGambling.getDayActivities().get(DayOfWeek.SATURDAY);
		assertThat(previousWeekSaturdayActivity.getTotalActivityDurationMinutes().get(), equalTo(45));
		assertThat(previousWeekSaturdayActivity.getTotalMinutesBeyondGoal(), equalTo(45));
		DayActivityDto previousWeekFridayActivity = weekActivityForGambling.getDayActivities().get(DayOfWeek.FRIDAY);
		assertThat(previousWeekFridayActivity.getTotalActivityDurationMinutes().get(), equalTo(0));

		// get the week the gambling goal was created
		weekOverview = weekOverviews.getContent().get(2);
		assertThat(weekOverview.getWeekActivities().size(), equalTo(1));
		weekActivityForGambling = weekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalId().equals(gamblingGoal.getId())).findAny().get();
		assertThat(weekActivityForGambling.getStartTime(), equalTo(getWeekStartTime(today.minusWeeks(2))));
		// TODO: mock day activity in this week?
		// int expectedNumberOfWeekDaysRecorded = gamblingGoal.getCreationTime().getDayOfWeek() == DayOfWeek.SUNDAY ? 7
		// : 7 - gamblingGoal.getCreationTime().getDayOfWeek().getValue();
		// assertThat(weekActivityForGambling.getDayActivities().size(), equalTo(expectedNumberOfWeekDaysRecorded));
		//// always contains Saturday because it is the last day of the week
		// assertThat(weekActivityForGambling.getDayActivities(), hasKey(DayOfWeek.SATURDAY));
	}

	@Test
	public void getUserDayActivityOverviews_noActivityPresent_resultsWithInactivity()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));

		Page<DayActivityOverviewDto<DayActivityDto>> inactivityDayOverviews = service.getUserDayActivityOverviews(userId,
				new PageRequest(0, 3));

		// because the gambling goal was added with creation date two weeks ago, there are multiple days
		assertThat(inactivityDayOverviews.getNumberOfElements(), equalTo(3));
		// the other goals were created today, so get the most recent (first) element
		DayActivityOverviewDto<DayActivityDto> inactivityDayOverview = inactivityDayOverviews.getContent().get(0);
		assertThat(inactivityDayOverview.getDayActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		DayActivityDto inactivityDayForGambling = inactivityDayOverview.getDayActivities().stream()
				.filter(a -> a.getGoalId().equals(gamblingGoal.getId())).findAny().get();
		assertThat(inactivityDayForGambling.getStartTime(), equalTo(today));
		assertThat(inactivityDayForGambling.getTotalActivityDurationMinutes().get(), equalTo(0));
		assertThat(inactivityDayForGambling.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void getUserWeekActivityOverviews_noActivityPresent_resultsWithInactivity()
	{
		Page<WeekActivityOverviewDto> inactivityWeekOverviews = service.getUserWeekActivityOverviews(userId,
				new PageRequest(0, 5));

		// because the gambling goal was added with creation date two weeks ago, there are multiple weeks
		assertThat(inactivityWeekOverviews.getNumberOfElements(), equalTo(3));
		// the other goals were created today, so get the most recent (first) element
		WeekActivityOverviewDto inactivityWeekOverview = inactivityWeekOverviews.getContent().get(0);
		assertThat(inactivityWeekOverview.getWeekActivities().size(), equalTo(userAnonEntity.getGoals().size()));
		WeekActivityDto inactivityWeekForGambling = inactivityWeekOverview.getWeekActivities().stream()
				.filter(a -> a.getGoalId().equals(gamblingGoal.getId())).findAny().get();
		assertThat(inactivityWeekForGambling.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		// TODO: mock day activity in this week?
		// ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));
		// int thisWeekNumberOfWeekDaysPast = today.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : today.getDayOfWeek().getValue();
		// assertThat(inactivityWeekForGambling.getDayActivities().size(), equalTo(1 + thisWeekNumberOfWeekDaysPast));
	}

	@Test
	public void getUserDayActivityDetail_noActivityPresent_resultWithInactivity()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));

		DayActivityDto inactivityDay = service.getUserDayActivityDetail(userId, LocalDate.now(userAnonZone),
				gamblingGoal.getId());

		assertThat(inactivityDay.getSpread().size(), equalTo(96));
		assertThat(inactivityDay.getStartTime(), equalTo(today));
		assertThat(inactivityDay.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityDay.getTotalActivityDurationMinutes().get(), equalTo(0));
		assertThat(inactivityDay.getTotalMinutesBeyondGoal(), equalTo(0));
	}

	@Test
	public void getUserWeekActivityDetail_noActivityPresent_resultWithInactivity()
	{
		WeekActivityDto inactivityWeek = service.getUserWeekActivityDetail(userId, getWeekStartDate(LocalDate.now(userAnonZone)),
				gamblingGoal.getId());

		assertThat(inactivityWeek.getSpread().size(), equalTo(96));
		assertThat(inactivityWeek.getStartTime(), equalTo(getWeekStartTime(ZonedDateTime.now(userAnonZone))));
		assertThat(inactivityWeek.getTimeZoneId(), equalTo(userAnonZone.getId()));
		assertThat(inactivityWeek.getTotalActivityDurationMinutes().get(), equalTo(0));
	}

	@Test
	public void getUserDayActivityDetail_activityPresent_resultWithActivity()
	{
		ZonedDateTime today = getDayStartTime(ZonedDateTime.now(userAnonZone));
		ZonedDateTime yesterday = today.minusDays(1);

		LocalTime activityStartTimeOnDay = LocalTime.parse("20:14:57");
		LocalTime activityEndTimeOnDay = LocalTime.parse("20:21:00");

		int hour = 20;
		int[] expectedSpread = getEmptySpread();
		expectedSpread[hour * 4] = 1;
		expectedSpread[hour * 4 + 1] = 6;

		// gambling goal was created 2 weeks ago, see above
		// mock some activity on yesterday
		DayActivity yesterdayRecordedActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, userAnonZone,
				yesterday.toLocalDate());
		ZonedDateTime activityStartTime = yesterday.withHour(activityStartTimeOnDay.getHour())
				.withMinute(activityStartTimeOnDay.getMinute()).withSecond(activityStartTimeOnDay.getSecond());
		ZonedDateTime activityEndTime = yesterday.withHour(activityEndTimeOnDay.getHour())
				.withMinute(activityEndTimeOnDay.getMinute()).withSecond(activityEndTimeOnDay.getSecond());
		Activity recordedActivity = Activity.createInstance(deviceAnonEntity, userAnonZone, activityStartTime.toLocalDateTime(),
				activityEndTime.toLocalDateTime(), Optional.empty());
		yesterdayRecordedActivity.addActivity(recordedActivity);
		when(mockDayActivityRepository.findOne(userAnonId, yesterday.toLocalDate(), gamblingGoal.getId()))
				.thenReturn(yesterdayRecordedActivity);

		DayActivityDto activityDay = service.getUserDayActivityDetail(userId, yesterday.toLocalDate(), gamblingGoal.getId());

		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, yesterday.toLocalDate(), gamblingGoal.getId());
		assertThat(activityDay.getSpread(), equalTo(Arrays.asList(ArrayUtils.toObject((expectedSpread)))));
	}

	private int[] getEmptySpread()
	{
		int[] expectedSpread = new int[96];
		Arrays.fill(expectedSpread, 0);
		return expectedSpread;
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
}