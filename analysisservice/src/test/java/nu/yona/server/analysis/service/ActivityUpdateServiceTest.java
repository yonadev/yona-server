/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.data.repository.Repository;

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.ActivityRepository;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.service.DeviceAnonymizedDto;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalRepository;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageRepository;
import nu.yona.server.messaging.service.MessageDestinationDto;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.AnalysisServiceProperties;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.TimeUtil;

@RunWith(MockitoJUnitRunner.class)
public class ActivityUpdateServiceTest
{
	private final Map<String, Goal> goalMap = new HashMap<>();

	@Mock
	private UserAnonymizedService mockUserAnonymizedService;
	@Mock
	private GoalService mockGoalService;
	@Mock
	private MessageService mockMessageService;
	@Mock
	private YonaProperties mockYonaProperties;
	@Mock
	private ActivityCacheService mockAnalysisEngineCacheService;
	@Mock
	private GoalRepository mockGoalRepository;
	@Mock
	private MessageRepository mockMessageRepository;
	@Mock
	private ActivityRepository mockActivityRepository;
	@Mock
	private DayActivityRepository mockDayActivityRepository;
	@Mock
	private WeekActivityRepository mockWeekActivityRepository;
	@Mock
	private DeviceAnonymizedRepository mockDeviceAnonymizedRepository;

	@InjectMocks
	private final ActivityUpdateService service = new ActivityUpdateService();

	private Goal gamblingGoal;
	private Goal newsGoal;
	private Goal gamingGoal;
	private Goal socialGoal;
	private Goal shoppingGoal;
	private MessageDestinationDto anonMessageDestination;
	private UUID userAnonId;
	private UUID deviceAnonId;
	private DeviceAnonymized deviceAnonEntity;
	private UserAnonymized userAnonEntity;
	private UserAnonymizedDto userAnonDto;

	private ZoneId userAnonZoneId;

	private DeviceAnonymizedDto deviceAnonDto;

	@Before
	public void setUp()
	{
		setUpRepositoryMocks();

		LocalDateTime yesterday = TimeUtil.utcNow().minusDays(1).withHour(0).withMinute(1).withSecond(0);
		gamblingGoal = BudgetGoal.createNoGoInstance(yesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
						new HashSet<>(Arrays.asList("poker", "lotto")), new HashSet<>(Arrays.asList("Poker App", "Lotto App")),
						usString("Descr")));
		newsGoal = BudgetGoal.createNoGoInstance(yesterday, ActivityCategory.createInstance(UUID.randomUUID(), usString("news"),
				false, new HashSet<>(Arrays.asList("refdag", "bbc")), Collections.emptySet(), usString("Descr")));
		gamingGoal = BudgetGoal.createNoGoInstance(yesterday, ActivityCategory.createInstance(UUID.randomUUID(),
				usString("gaming"), false, new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr")));
		socialGoal = TimeZoneGoal.createInstance(yesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("social"), false,
						new HashSet<>(Arrays.asList("social")), Collections.emptySet(), usString("Descr")),
				Collections.emptyList());
		shoppingGoal = BudgetGoal.createInstance(yesterday, ActivityCategory.createInstance(UUID.randomUUID(),
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
		deviceAnonEntity = DeviceAnonymized.createInstance(0, OperatingSystem.IOS, "Unknown", Optional.empty());
		deviceAnonId = deviceAnonEntity.getId();
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		userAnonEntity.addDeviceAnonymized(deviceAnonEntity);
		userAnonDto = UserAnonymizedDto.createInstance(userAnonEntity);
		deviceAnonDto = DeviceAnonymizedDto.createInstance(deviceAnonEntity);
		anonMessageDestination = userAnonDto.getAnonymousDestination();
		userAnonId = userAnonDto.getId();
		userAnonZoneId = userAnonDto.getTimeZone();

		// Stub the UserAnonymizedService to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonId)).thenReturn(userAnonDto);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonId)).thenReturn(userAnonEntity);

		// Stub the GoalService to return our goals.
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamblingGoal.getId())).thenReturn(gamblingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamingGoal.getId())).thenReturn(gamingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, socialGoal.getId())).thenReturn(socialGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, shoppingGoal.getId())).thenReturn(shoppingGoal);

		// Mock the week activity repository
		when(mockWeekActivityRepository.findOne(any(UUID.class), any(UUID.class), any(LocalDate.class)))
				.thenAnswer(new Answer<WeekActivity>() {
					@Override
					public WeekActivity answer(InvocationOnMock invocation) throws Throwable
					{
						Optional<Goal> goal = goalMap.values().stream()
								.filter(g -> g.getId() == invocation.getArgumentAt(1, UUID.class)).findAny();
						if (!goal.isPresent())
						{
							return null;
						}
						return goal.get().getWeekActivities().stream()
								.filter(wa -> wa.getStartDate().equals(invocation.getArgumentAt(2, LocalDate.class))).findAny()
								.orElse(null);
					}
				});

		// Mock device service and repo
		when(mockDeviceAnonymizedRepository.getOne(deviceAnonId)).thenReturn(deviceAnonEntity);
	}

	private void setUpRepositoryMocks()
	{
		JUnitUtil.setUpRepositoryMock(mockGoalRepository);
		JUnitUtil.setUpRepositoryMock(mockMessageRepository);
		JUnitUtil.setUpRepositoryMock(mockActivityRepository);
		JUnitUtil.setUpRepositoryMock(mockDayActivityRepository);
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(Goal.class, mockGoalRepository);
		repositoriesMap.put(Message.class, mockMessageRepository);
		repositoriesMap.put(Activity.class, mockActivityRepository);
		repositoriesMap.put(DayActivity.class, mockDayActivityRepository);
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	@Test
	public void addActivity_noLastRegisteredActivity_goalConflictMessageCreated()
	{
		ZonedDateTime t1 = now();

		service.addActivity(userAnonEntity, createPayload(t1, t1), GoalDto.createInstance(gamblingGoal), Optional.empty());

		verifyGoalConflictMessageCreated(gamblingGoal);
	}

	@Test
	public void addActivity_timeZoneGoal_noGoalConflictMessagesCreated()
	{
		ZonedDateTime t1 = now();

		service.addActivity(userAnonEntity, createPayload(t1, t1), GoalDto.createInstance(shoppingGoal), Optional.empty());

		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void addActivity_nonZeroBudgetGoal_noGoalConflictMessagesCreated()
	{
		ZonedDateTime t1 = now();

		service.addActivity(userAnonEntity, createPayload(t1, t1), GoalDto.createInstance(socialGoal), Optional.empty());

		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void addActivity_afterConflictIntervalLastRegisteredActivity_goalConflictMessageCreated()
	{
		ZonedDateTime t1 = now();
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(createActivity(t1, t1));
		ZonedDateTime t2 = t1.plus(mockYonaProperties.getAnalysisService().getConflictInterval()).plusSeconds(1);

		service.addActivity(userAnonEntity, createPayload(t2, t2), GoalDto.createInstance(gamblingGoal),
				Optional.of(lastRegisteredActivity));

		verifyGoalConflictMessageCreated(gamblingGoal);
	}

	@Test
	public void addActivity_withinConflictIntervalLastRegisteredActivity_noGoalConflictMessagesCreated()
	{
		ZonedDateTime t1 = now();
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(createActivity(t1, t1));
		ZonedDateTime t2 = t1.plus(mockYonaProperties.getAnalysisService().getConflictInterval()).minusSeconds(1);

		service.addActivity(userAnonEntity, createPayload(t2, t2), GoalDto.createInstance(gamblingGoal),
				Optional.of(lastRegisteredActivity));

		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void addActivity_completelyPrecedingLastRegisteredActivity_noGoalConflictMessagesCreated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(createActivity(t2, t2));

		service.addActivity(userAnonEntity, createPayload(t1, t1), GoalDto.createInstance(gamblingGoal),
				Optional.of(lastRegisteredActivity));

		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void addActivity_default_activityUpdateWithCorrectTimes()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusMinutes(2);

		service.addActivity(userAnonEntity, createPayload(t1, t2), GoalDto.createInstance(gamblingGoal), Optional.empty());

		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity present or created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity present or created", dayActivities.size(), equalTo(1));
		List<Activity> activities = dayActivities.get(0).getActivities();
		assertThat("One activity present or created", activities.size(), equalTo(1));
		Activity activity = activities.get(0);
		assertThat("Expect right goal set to activity", activity.getActivityCategory(),
				equalTo(gamblingGoal.getActivityCategory()));
		assertThat(activity.getStartTimeAsZonedDateTime(), equalTo(t1));
		assertThat(activity.getEndTimeAsZonedDateTime(), equalTo(t2));
	}

	@Test
	public void addActivity_durationLessThanOneMinute_minimumDurationOneMinute()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(59);

		service.addActivity(userAnonEntity, createPayload(t1, t2), GoalDto.createInstance(gamblingGoal), Optional.empty());

		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity present or created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity present or created", dayActivities.size(), equalTo(1));
		List<Activity> activities = dayActivities.get(0).getActivities();
		assertThat("One activity present or created", activities.size(), equalTo(1));
		Activity activity = activities.get(0);
		assertThat("Expect right goal set to activity", activity.getActivityCategory(),
				equalTo(gamblingGoal.getActivityCategory()));
		assertThat(activity.getStartTimeAsZonedDateTime(), equalTo(t1));
		assertThat(activity.getEndTimeAsZonedDateTime(), equalTo(t1.plusMinutes(1)));
	}

	@Test
	public void addActivity_appActivityOnNewDay_newDayActivityButNoGoalConflictMessageCreated()
	{
		ZonedDateTime today = now().truncatedTo(ChronoUnit.DAYS);
		// mock earlier activity at yesterday 23:59:58,
		// add new activity at today 00:00:01
		ZonedDateTime existingActivityTime = today.minusDays(1).withHour(23).withMinute(59).withSecond(58);

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, existingActivityTime);
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(existingDayActivity.getActivities().get(0));

		ZonedDateTime startTime = today.withHour(0).withMinute(0).withSecond(1);
		ZonedDateTime endTime = today.withHour(0).withMinute(10);

		service.addActivity(userAnonEntity, createPayload(startTime, endTime), GoalDto.createInstance(gamblingGoal),
				Optional.of(lastRegisteredActivity));

		verifyNoGoalConflictMessagesCreated();

		// Verify there are now two day activities
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonEntity);
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("Two day activities created", dayActivities.size(), equalTo(2));
		DayActivity yesterdaysDayActivity;
		DayActivity todaysDayActivity;
		if (dayActivities.get(0).getStartDate().isBefore(dayActivities.get(1).getStartDate()))
		{
			yesterdaysDayActivity = dayActivities.get(0);
			todaysDayActivity = dayActivities.get(1);
		}
		else
		{
			yesterdaysDayActivity = dayActivities.get(1);
			todaysDayActivity = dayActivities.get(0);
		}

		// Double check yesterday's activity
		List<Activity> yesterdaysActivities = yesterdaysDayActivity.getActivities();
		assertThat("One activity created for yesterday", yesterdaysActivities.size(), equalTo(1));
		Activity yesterdaysActivity = yesterdaysActivities.get(0);
		assertThat("Expect right goal set to yesterday's activity", yesterdaysActivity.getActivityCategory(),
				equalTo(gamblingGoal.getActivityCategory()));

		// Verify one activity was created, with the right goal
		List<Activity> activities = todaysDayActivity.getActivities();
		assertThat("One activity created", activities.size(), equalTo(1));
		Activity activity = activities.get(0);
		assertThat("Expect right goal set to activity", activity.getActivityCategory(),
				equalTo(gamblingGoal.getActivityCategory()));

		assertThat("Expect new day", todaysDayActivity, not(equalTo(existingDayActivity)));
		assertThat("Expect right date", todaysDayActivity.getStartDate(), equalTo(today.toLocalDate()));
		assertThat("Expect activity added", todaysDayActivity.getLastActivity(deviceAnonId), notNullValue());
		assertThat("Expect matching start time", todaysDayActivity.getLastActivity(deviceAnonId).getStartTime(),
				equalTo(startTime.toLocalDateTime()));
		assertThat("Expect matching end time", todaysDayActivity.getLastActivity(deviceAnonId).getEndTime(),
				equalTo(endTime.toLocalDateTime()));

		// Verify that there is an activity cached
		verify(mockAnalysisEngineCacheService, atLeastOnce()).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());
	}

	@Test
	public void updateTimeLastActivity_startTimeEarlier_activityStartTimeUpdated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		ZonedDateTime t3 = t2.plusSeconds(1);
		DayActivity existingDayActivityEntity = mockExistingActivity(gamblingGoal, t2, t3, "Lotto");
		Activity existingActivityEntity = existingDayActivityEntity.getLastActivity(deviceAnonId);
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(existingActivityEntity);

		service.updateTimeLastActivity(createPayload(t1, t2), GoalDto.createInstance(gamblingGoal), lastRegisteredActivity);

		assertThat("Expect start time updated", existingActivityEntity.getStartTimeAsZonedDateTime(), equalTo(t1));
		assertThat("Expect end time same", existingActivityEntity.getEndTimeAsZonedDateTime(), equalTo(t3));
	}

	@Test
	public void updateTimeLastActivity_endTimeLater_activityEndTimeUpdated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		ZonedDateTime t3 = t2.plusSeconds(1);
		DayActivity existingDayActivityEntity = mockExistingActivity(gamblingGoal, t1, t2, "Lotto");
		Activity existingActivityEntity = existingDayActivityEntity.getLastActivity(deviceAnonId);
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(existingActivityEntity);

		service.updateTimeLastActivity(createPayload(t2, t3), GoalDto.createInstance(gamblingGoal), lastRegisteredActivity);

		assertThat("Expect start time same", existingActivityEntity.getStartTimeAsZonedDateTime(), equalTo(t1));
		assertThat("Expect end time updated", existingActivityEntity.getEndTimeAsZonedDateTime(), equalTo(t3));
	}

	@Test
	public void updateTimeLastActivity_default_cacheUpdated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		DayActivity existingDayActivityEntity = mockExistingActivity(gamblingGoal, t2, t2, "Lotto");
		Activity existingActivityEntity = existingDayActivityEntity.getLastActivity(deviceAnonId);
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(existingActivityEntity);

		service.updateTimeLastActivity(createPayload(t1, t2), GoalDto.createInstance(gamblingGoal), lastRegisteredActivity);

		verify(mockAnalysisEngineCacheService).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());
	}

	@Test
	public void updateTimeLastActivity_default_noGoalConflictMessagesCreated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		ZonedDateTime t3 = t2.plusSeconds(1);
		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, t2);
		ActivityDto lastRegisteredActivity = ActivityDto.createInstance(existingDayActivity.getActivities().get(0));

		service.updateTimeLastActivity(createPayload(t1, t3), GoalDto.createInstance(gamblingGoal), lastRegisteredActivity);

		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void updateTimeExistingActivity_startTimeEarlier_activityStartTimeUpdated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		ZonedDateTime t3 = t2.plusSeconds(1);
		DayActivity existingDayActivityEntity = mockExistingActivity(gamblingGoal, t2, t3, "Lotto");
		Activity existingActivityEntity = existingDayActivityEntity.getLastActivity(deviceAnonId);

		service.updateTimeExistingActivity(createPayload(t1, t2), existingActivityEntity);

		assertThat("Expect start time updated", existingActivityEntity.getStartTimeAsZonedDateTime(), equalTo(t1));
		assertThat("Expect end time same", existingActivityEntity.getEndTimeAsZonedDateTime(), equalTo(t3));
	}

	@Test
	public void updateTimeExistingActivity_endTimeLater_activityEndTimeUpdated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		ZonedDateTime t3 = t2.plusSeconds(1);
		DayActivity existingDayActivityEntity = mockExistingActivity(gamblingGoal, t1, t2, "Lotto");
		Activity existingActivityEntity = existingDayActivityEntity.getLastActivity(deviceAnonId);

		service.updateTimeExistingActivity(createPayload(t2, t3), existingActivityEntity);

		assertThat("Expect start time same", existingActivityEntity.getStartTimeAsZonedDateTime(), equalTo(t1));
		assertThat("Expect end time updated", existingActivityEntity.getEndTimeAsZonedDateTime(), equalTo(t3));
	}

	@Test
	public void updateTimeExistingActivity_default_cacheNotUpdated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		DayActivity existingDayActivityEntity = mockExistingActivity(gamblingGoal, t2, t2, "Lotto");
		Activity existingActivityEntity = existingDayActivityEntity.getLastActivity(deviceAnonId);

		service.updateTimeExistingActivity(createPayload(t1, t2), existingActivityEntity);

		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(any(), any(), any(), any());
	}

	@Test
	public void updateTimeExistingActivity_default_noGoalConflictMessagesCreated()
	{
		ZonedDateTime t1 = now();
		ZonedDateTime t2 = t1.plusSeconds(1);
		ZonedDateTime t3 = t2.plusSeconds(1);
		DayActivity existingDayActivityEntity = mockExistingActivity(gamblingGoal, t2, t2, "Lotto");
		Activity existingActivityEntity = existingDayActivityEntity.getLastActivity(deviceAnonId);

		service.updateTimeExistingActivity(createPayload(t1, t3), existingActivityEntity);

		verifyNoGoalConflictMessagesCreated();
	}

	private void verifyGoalConflictMessageCreated(Goal... forGoals)
	{
		ArgumentCaptor<GoalConflictMessage> messageCaptor = ArgumentCaptor.forClass(GoalConflictMessage.class);
		ArgumentCaptor<MessageDestination> messageDestinationCaptor = ArgumentCaptor.forClass(MessageDestination.class);
		verify(mockMessageService, times(forGoals.length)).sendMessage(messageCaptor.capture(),
				messageDestinationCaptor.capture());
		assertThat("Expect right message destination", messageDestinationCaptor.getValue().getId(),
				equalTo(anonMessageDestination.getId()));
		assertThat("Expected right related user set to goal conflict message",
				messageCaptor.getValue().getRelatedUserAnonymizedId().get(), equalTo(userAnonId));
		assertThat("Expected right goal set to goal conflict message",
				messageCaptor.getAllValues().stream().map(m -> m.getGoal().getId()).collect(Collectors.toList()),
				containsInAnyOrder(Arrays.stream(forGoals).map(Goal::getId).collect(Collectors.toList()).toArray()));
	}

	private void verifyNoGoalConflictMessagesCreated()
	{
		verify(mockMessageService, never()).sendMessage(any(), any());
	}

	private DayActivity mockExistingActivity(Goal forGoal, ZonedDateTime activityTime)
	{
		return mockExistingActivities(forGoal, createActivity(activityTime, activityTime));
	}

	private DayActivity mockExistingActivity(Goal forGoal, ZonedDateTime startTime, ZonedDateTime endTime, String app)
	{
		return mockExistingActivities(forGoal, createActivity(startTime, endTime, app));
	}

	private DayActivity mockExistingActivities(Goal forGoal, Activity... activities)
	{
		LocalDateTime startTime = activities[0].getStartTime();
		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, forGoal, userAnonZoneId,
				startTime.truncatedTo(ChronoUnit.DAYS).toLocalDate());
		Arrays.asList(activities).forEach(a -> dayActivity.addActivity(a));
		ActivityDto existingActivity = ActivityDto.createInstance(activities[activities.length - 1]);
		when(mockDayActivityRepository.findOne(userAnonId, dayActivity.getStartDate(), forGoal.getId())).thenReturn(dayActivity);
		when(mockAnalysisEngineCacheService.fetchLastActivityForUser(userAnonId, deviceAnonId, forGoal.getId()))
				.thenReturn(existingActivity);
		WeekActivity weekActivity = WeekActivity.createInstance(userAnonEntity, forGoal, userAnonZoneId,
				TimeUtil.getStartOfWeek(startTime.toLocalDate()));
		weekActivity.addDayActivity(dayActivity);
		forGoal.addWeekActivity(weekActivity);
		return dayActivity;
	}

	private Activity createActivity(ZonedDateTime startTime, ZonedDateTime endTime)
	{
		return Activity.createInstance(deviceAnonEntity, userAnonZoneId, startTime.toLocalDateTime(), endTime.toLocalDateTime(),
				Optional.empty());
	}

	private Activity createActivity(ZonedDateTime startTime, ZonedDateTime endTime, String app)
	{
		return Activity.createInstance(deviceAnonEntity, userAnonZoneId, startTime.toLocalDateTime(), endTime.toLocalDateTime(),
				Optional.of(app));
	}

	private ZonedDateTime now()
	{
		return ZonedDateTime.now().withZoneSameInstant(userAnonZoneId);
	}

	private ActivityPayload createPayload(ZonedDateTime startTime, ZonedDateTime endTime)
	{
		return ActivityPayload.createInstance(userAnonDto, deviceAnonDto, startTime, endTime, "Lotto",
				makeCategorySet(gamblingGoal));
	}

	private Set<ActivityCategoryDto> makeCategorySet(Goal... goals)
	{
		return Arrays.asList(goals).stream().map(Goal::getActivityCategory).map(ActivityCategoryDto::createInstance)
				.collect(Collectors.toSet());
	}
}