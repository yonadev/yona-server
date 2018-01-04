/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
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
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalService;
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
import nu.yona.server.util.LockPool;
import nu.yona.server.util.TimeUtil;
import nu.yona.server.util.TransactionHelper;

@RunWith(MockitoJUnitRunner.class)
public class AnalysisEngineServiceTest
{
	private final Map<String, Goal> goalMap = new HashMap<>();

	@Mock
	private ActivityCategoryService mockActivityCategoryService;
	@Mock
	private ActivityCategoryService.FilterService mockActivityCategoryFilterService;
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
	private MessageRepository mockMessageRepository;
	@Mock
	private ActivityRepository mockActivityRepository;
	@Mock
	private DayActivityRepository mockDayActivityRepository;
	@Mock
	private WeekActivityRepository mockWeekActivityRepository;
	@Mock
	private LockPool<UUID> userAnonymizedSynchronizer;
	@Mock
	private TransactionHelper transactionHelper;
	@Mock
	private DeviceService mockDeviceService;
	@Mock
	private DeviceAnonymizedRepository mockDeviceAnonymizedRepository;
	@Mock
	private Appender<ILoggingEvent> mockLogAppender;

	@InjectMocks
	private final AnalysisEngineService service = new AnalysisEngineService();

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

	private ZoneId userAnonZoneId;

	@Before
	public void setUp()
	{
		Logger logger = (Logger) LoggerFactory.getLogger(AnalysisEngineService.class);
		logger.addAppender(mockLogAppender);

		LocalDateTime yesterday = TimeUtil.utcNow().minusDays(1);
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

		when(mockActivityCategoryService.getAllActivityCategories()).thenReturn(getAllActivityCategories());
		when(mockActivityCategoryFilterService.getMatchingCategoriesForSmoothwallCategories(anySetOf(String.class)))
				.thenAnswer(new Answer<Set<ActivityCategoryDto>>() {
					@Override
					public Set<ActivityCategoryDto> answer(InvocationOnMock invocation) throws Throwable
					{
						Object[] args = invocation.getArguments();
						@SuppressWarnings("unchecked")
						Set<String> smoothwallCategories = (Set<String>) args[0];
						return getAllActivityCategories()
								.stream().filter(ac -> ac.getSmoothwallCategories().stream()
										.filter(smoothwallCategories::contains).findAny().isPresent())
								.collect(Collectors.toSet());
					}
				});
		when(mockActivityCategoryFilterService.getMatchingCategoriesForApp(any(String.class)))
				.thenAnswer(new Answer<Set<ActivityCategoryDto>>() {
					@Override
					public Set<ActivityCategoryDto> answer(InvocationOnMock invocation) throws Throwable
					{
						Object[] args = invocation.getArguments();
						String application = (String) args[0];
						return getAllActivityCategories().stream().filter(ac -> ac.getApplications().contains(application))
								.collect(Collectors.toSet());
					}
				});

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<>(Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal));
		deviceAnonEntity = DeviceAnonymized.createInstance(0, OperatingSystem.ANDROID, "Unknown");
		deviceAnonId = deviceAnonEntity.getId();
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		userAnonEntity.addDeviceAnonymized(deviceAnonEntity);
		UserAnonymizedDto userAnon = UserAnonymizedDto.createInstance(userAnonEntity);
		anonMessageDestination = userAnon.getAnonymousDestination();
		userAnonId = userAnon.getId();
		userAnonZoneId = userAnon.getTimeZone();

		// Stub the UserAnonymizedService to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonId)).thenReturn(userAnon);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonId)).thenReturn(userAnonEntity);

		// Stub the GoalService to return our goals.
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamblingGoal.getId())).thenReturn(gamblingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamingGoal.getId())).thenReturn(gamingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, socialGoal.getId())).thenReturn(socialGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, shoppingGoal.getId())).thenReturn(shoppingGoal);

		// Mock the transaction helper
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable
			{
				invocation.getArgumentAt(0, Runnable.class).run();
				return null;
			}
		}).when(transactionHelper).executeInNewTransaction(any(Runnable.class));

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
		when(mockDeviceService.getDeviceAnonymizedId(userAnon, -1)).thenReturn(deviceAnonId);
		when(mockDeviceAnonymizedRepository.getOne(deviceAnonId)).thenReturn(deviceAnonEntity);

		// Set up the repository mocks
		JUnitUtil.setUpRepositoryMock(mockMessageRepository);
		JUnitUtil.setUpRepositoryMock(mockActivityRepository);
		JUnitUtil.setUpRepositoryMock(mockDayActivityRepository);
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	private Set<ActivityCategoryDto> getAllActivityCategories()
	{
		return goalMap.values().stream().map(goal -> ActivityCategoryDto.createInstance(goal.getActivityCategory()))
				.collect(Collectors.toSet());
	}

	@Test
	public void getRelevantSmoothwallCategories_default_containsExpectedItems()
	{
		Set<String> result = service.getRelevantSmoothwallCategories();

		assertThat(result, containsInAnyOrder("poker", "lotto", "refdag", "bbc", "games", "social", "webshop"));
	}

	@Test
	public void analyze_secondConflictAfterConflictInterval_newGoalConflictMessageCreated()
	{
		// Normally there is one conflict message sent.
		// Set a short conflict interval such that the conflict messages are not aggregated.
		AnalysisServiceProperties p = new AnalysisServiceProperties();
		p.setUpdateSkipWindow("PT0.001S");
		p.setConflictInterval("PT0.01S");
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		mockExistingActivity(gamblingGoal, now());

		// Execute the analysis engine service after a period of inactivity longer than the conflict interval.

		try
		{
			Thread.sleep(11L);
		}
		catch (InterruptedException e)
		{

		}

		service.analyze(userAnonId, createNetworkActivityForCategories("lotto"));

		verifyGoalConflictMessageCreated(gamblingGoal);
	}

	@Test
	public void analyze_secondConflictWithinConflictInterval_noNewGoalConflictMessagesCreated()
	{
		mockExistingActivity(gamblingGoal, now());

		service.analyze(userAnonId, createNetworkActivityForCategories("lotto"));

		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void analyze_matchingCategory_activityUpdateWithCorrectTimesAndGoalConflictMessageCreated()
	{
		ZonedDateTime justBeforeAnalyzeCall = now();

		service.analyze(userAnonId, createNetworkActivityForCategories("lotto"));

		ZonedDateTime justAfterAnalyzeCall = now();
		List<Activity> resultActivities = verifyActivityUpdate(gamblingGoal);
		Activity activity = resultActivities.get(0);
		assertThat(activity.getStartTimeAsZonedDateTime(), greaterThanOrEqualTo(justBeforeAnalyzeCall));
		assertThat(activity.getStartTimeAsZonedDateTime(), lessThanOrEqualTo(justAfterAnalyzeCall));
		assertThat(activity.getEndTimeAsZonedDateTime(), greaterThanOrEqualTo(justBeforeAnalyzeCall));
		assertThat(activity.getEndTimeAsZonedDateTime(), lessThanOrEqualTo(justAfterAnalyzeCall.plusMinutes(1)));
		verifyGoalConflictMessageCreated(gamblingGoal);
	}

	@Test
	public void analyze_matchOneCategoryOfMultiple_oneActivityUpdateAndGoalConflictMessageCreated()
	{
		service.analyze(userAnonId, createNetworkActivityForCategories("refdag", "lotto"));

		verifyActivityUpdate(gamblingGoal);
		verifyGoalConflictMessageCreated(gamblingGoal);
	}

	@Test
	public void analyze_multipleMatchingCategories_multipleActivityUpdatesAndGoalConflictMessagesCreated()
	{
		service.analyze(userAnonId, createNetworkActivityForCategories("lotto", "games"));

		verifyActivityUpdate(gamblingGoal, gamingGoal);
		verifyGoalConflictMessageCreated(gamblingGoal, gamingGoal);
	}

	@Test
	public void analyze_multipleFollowingCallsWithinConflictInterval_activityUpdatedButNoNewGoalConflictMessagesCreated()
	{
		// Normally there is one conflict message sent.
		// Set update skip window to 0 such that the conflict messages are aggregated immediately.
		AnalysisServiceProperties p = new AnalysisServiceProperties();
		p.setUpdateSkipWindow("PT0S");
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		ZonedDateTime timeOfMockedExistingActivity = now();
		DayActivity dayActivity = mockExistingActivity(gamblingGoal, timeOfMockedExistingActivity);
		Activity existingActivityEntity = dayActivity.getLastActivity(deviceAnonId);

		service.analyze(userAnonId, createNetworkActivityForCategories("refdag")); // not matching category
		service.analyze(userAnonId, createNetworkActivityForCategories("lotto")); // matching category
		service.analyze(userAnonId, createNetworkActivityForCategories("poker")); // matching category
		service.analyze(userAnonId, createNetworkActivityForCategories("refdag")); // not matching category
		ZonedDateTime justBeforeLastAnalyzeCall = now();
		service.analyze(userAnonId, createNetworkActivityForCategories("poker")); // matching category

		// Verify that the cache is used to check existing activity
		verify(mockAnalysisEngineCacheService, times(3)).fetchLastActivityForUser(userAnonId, deviceAnonId, gamblingGoal.getId());
		verifyNoGoalConflictMessagesCreated();
		// Verify that the existing day activity was updated.
		verifyActivityUpdate(gamblingGoal);
		// Verify that the existing activity was updated in the cache.
		verify(mockAnalysisEngineCacheService, times(3)).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());
		assertThat("Expect start time to remain the same", existingActivityEntity.getStartTime(),
				equalTo(timeOfMockedExistingActivity.toLocalDateTime()));
		assertThat("Expect end time updated at the last executed analysis matching the goals",
				existingActivityEntity.getEndTimeAsZonedDateTime(), greaterThanOrEqualTo(justBeforeLastAnalyzeCall));
	}

	@Test
	public void analyze_noMatchingCategory_noActivityUpdateAndNoGoalConflictMessagesCreated()
	{
		service.analyze(userAnonId, createNetworkActivityForCategories("refdag"));

		verify(mockAnalysisEngineCacheService, never()).fetchLastActivityForUser(eq(userAnonId), eq(deviceAnonId), any());
		verifyNoActivityUpdate();
		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void analyze_matchTimeZoneGoalCategory_activityUpdateButNoGoalConflictMessagesCreated()
	{
		service.analyze(userAnonId, createNetworkActivityForCategories("webshop"));

		verifyActivityUpdate(shoppingGoal);
		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void analyze_matchNonZeroBudgetGoalCategory_activityUpdateButNoGoalConflictMessagesCreated()
	{
		service.analyze(userAnonId, createNetworkActivityForCategories("social"));

		verifyActivityUpdate(socialGoal);
		verifyNoGoalConflictMessagesCreated();
	}

	@Test
	public void analyze_appActivityOnNewDay_newDayActivityButNoGoalConflictMessageCreated()
	{
		ZonedDateTime today = now().truncatedTo(ChronoUnit.DAYS);
		// mock earlier activity at yesterday 23:59:58,
		// add new activity at today 00:00:01
		ZonedDateTime existingActivityTime = today.minusDays(1).withHour(23).withMinute(59).withSecond(58);

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, existingActivityTime);

		ZonedDateTime startTime = today.withHour(0).withMinute(0).withSecond(1);
		ZonedDateTime endTime = today.withHour(0).withMinute(10);

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Poker App", startTime, endTime));

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
	public void analyze_appActivityCompletelyPrecedingLastCachedActivity_existingActivityUpdatedButNoNewGoalConflictMessagesCreated()
	{
		ZonedDateTime now = now();
		ZonedDateTime existingActivityStartTime = now.minusMinutes(4);
		ZonedDateTime existingActivityEndTime = existingActivityStartTime.plusMinutes(2);

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, existingActivityStartTime, existingActivityEndTime,
				"Poker App");
		Activity existingActivity = existingDayActivity.getLastActivity(deviceAnonId);

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = startTime.plusMinutes(5);

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		verifyNoGoalConflictMessagesCreated();

		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, atLeastOnce()).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());

		// Verify that the day was updated
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonEntity);
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity created", dayActivities.size(), equalTo(1));
		DayActivity dayActivity = dayActivities.get(0);

		// Verify that the activity was not updated in the cache, because the end time is before the existing cached item
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());

		assertThat("Expect last activity unchanged", dayActivity.getLastActivity(deviceAnonId), equalTo(existingActivity));
		assertThat("Expect unchanged start time for last activity", dayActivity.getLastActivity(deviceAnonId).getStartTime(),
				equalTo(existingActivityStartTime.toLocalDateTime()));
		assertThat("Expect unchanged end time", dayActivity.getLastActivity(deviceAnonId).getEndTime(),
				equalTo(existingActivityEndTime.toLocalDateTime()));
		assertThat("Expect two activities on day", dayActivity.getActivities().size(), equalTo(2));
		Optional<Activity> addedActivity = dayActivity.getActivities().stream().filter(
				a -> a.getStartTime().equals(startTime.toLocalDateTime()) && a.getEndTime().equals(endTime.toLocalDateTime()))
				.findFirst();
		assertTrue("Expect that new activity is added", addedActivity.isPresent());
	}

	@Test
	public void analyze_appActivityCompletelyPrecedingLastCachedActivityOverlappingExistingActivity_existingActivityUpdatedButNoNewGoalConflictMessagesCreated()
	{
		ZonedDateTime now = now();
		ZonedDateTime existingActivityTimeStartTime = now.minusMinutes(20);
		ZonedDateTime existingActivityTimeEndTime = existingActivityTimeStartTime.plusMinutes(10);

		Activity existingActivityOne = createActivity(existingActivityTimeStartTime, existingActivityTimeEndTime, "Poker App");
		Activity existingActivityTwo = createActivity(now, now, "Poker App");
		mockExistingActivities(gamblingGoal, existingActivityOne, existingActivityTwo);

		when(mockActivityRepository.findOverlappingOfSameApp(any(DayActivity.class), any(UUID.class), any(UUID.class),
				any(String.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenAnswer(new Answer<List<Activity>>() {
					@Override
					public List<Activity> answer(InvocationOnMock invocation) throws Throwable
					{
						return Arrays.asList(existingActivityOne);
					}
				});

		// Test an activity
		ZonedDateTime startTime = existingActivityTimeStartTime.plusMinutes(5);
		ZonedDateTime endTime = startTime.plusMinutes(7);

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		verifyNoGoalConflictMessagesCreated();

		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, atLeastOnce()).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());

		// Verify that the day was updated
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonEntity);
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity created", dayActivities.size(), equalTo(1));
		DayActivity dayActivity = dayActivities.get(0);

		// Verify that the activity was not updated. An existing old activity should be updated, not the cache
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());

		assertThat("Expect last activity unchanged", dayActivity.getLastActivity(deviceAnonId), equalTo(existingActivityTwo));
		assertThat("Expect right activity start time", existingActivityOne.getStartTime(),
				equalTo(existingActivityTimeStartTime.toLocalDateTime()));
		assertThat("Expect right activity end time", existingActivityOne.getEndTime(), equalTo(endTime.toLocalDateTime()));
	}

	@Test
	public void analyze_networkActivityCompletelyPrecedingLastCachedActivityOverlappingMultipleExistingActivities_firstActivityRecordMergedAndNoNewGoalConflictMessageCreated()
	{
		ZonedDateTime now = now();
		DayActivity existingDayActivity = mockExistingActivities(gamblingGoal,
				createActivity(now.minusMinutes(10), now.minusMinutes(8)), createActivity(now.minusMinutes(1), now));
		when(mockActivityRepository.findOverlappingOfSameApp(any(DayActivity.class), any(UUID.class), any(UUID.class),
				any(String.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenAnswer(new Answer<List<Activity>>() {
					@Override
					public List<Activity> answer(InvocationOnMock invocation) throws Throwable
					{
						return existingDayActivity.getActivities().stream().collect(Collectors.toList());
					}
				});
		String expectedWarnMessage = MessageFormat.format(
				"Multiple overlapping network activities found. The payload has start time {0} and end time {1}. The day activity ID is {2} and the activity category ID is {3}. The overlapping activities are: {4}, {5}.",
				now.minusMinutes(9).toLocalDateTime(), now.minusMinutes(9).toLocalDateTime(), existingDayActivity.getId(),
				gamblingGoal.getActivityCategory().getId(), existingDayActivity.getActivities().get(0),
				existingDayActivity.getActivities().get(1));

		service.analyze(userAnonId, createNetworkActivityForCategories(now.minusMinutes(9), "poker"));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(2));
		assertThat(activities.get(0).getApp(), equalTo(Optional.empty()));
		assertThat(activities.get(0).getStartTimeAsZonedDateTime(), equalTo(now.minusMinutes(10)));
		assertThat(activities.get(0).getEndTimeAsZonedDateTime(), equalTo(now.minusMinutes(8)));
		assertThat(activities.get(1).getApp(), equalTo(Optional.empty()));
		assertThat(activities.get(1).getStartTimeAsZonedDateTime(), equalTo(now.minusMinutes(1)));
		assertThat(activities.get(1).getEndTimeAsZonedDateTime(), equalTo(now));

		ArgumentCaptor<ILoggingEvent> logEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
		verify(mockLogAppender).doAppend(logEventCaptor.capture());
		assertThat(logEventCaptor.getValue().getLevel(), equalTo(Level.WARN));
		assertThat(logEventCaptor.getValue().getFormattedMessage(), equalTo(expectedWarnMessage));
	}

	@Test
	public void analyze_appActivityCompletelyPrecedingLastCachedActivityOverlappingMultipleExistingActivities_firstActivityRecordMergedAndNoNewGoalConflictMessageCreated()
	{
		ZonedDateTime now = now();
		DayActivity existingDayActivity = mockExistingActivities(gamblingGoal,
				createActivity(now.minusMinutes(10), now.minusMinutes(8), "Lotto App"),
				createActivity(now.minusMinutes(7), now.minusMinutes(5), "Lotto App"), createActivity(now, now, "Lotto App"));
		when(mockActivityRepository.findOverlappingOfSameApp(any(DayActivity.class), any(UUID.class), any(UUID.class),
				any(String.class), any(LocalDateTime.class), any(LocalDateTime.class))).thenAnswer(new Answer<List<Activity>>() {
					@Override
					public List<Activity> answer(InvocationOnMock invocation) throws Throwable
					{
						return existingDayActivity.getActivities().stream().collect(Collectors.toList());
					}
				});
		String expectedWarnMessage = MessageFormat.format(
				"Multiple overlapping app activities of ''Lotto App'' found. The payload has start time {0} and end time {1}. The day activity ID is {2} and the activity category ID is {3}. The overlapping activities are: {4}, {5}, {6}.",
				now.minusMinutes(9).toLocalDateTime(), now.minusMinutes(2).toLocalDateTime(), existingDayActivity.getId(),
				gamblingGoal.getActivityCategory().getId(), existingDayActivity.getActivities().get(0),
				existingDayActivity.getActivities().get(1), existingDayActivity.getActivities().get(2));

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Lotto App", now.minusMinutes(9), now.minusMinutes(2)));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(3));
		assertThat(activities.get(0).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(0).getStartTimeAsZonedDateTime(), equalTo(now.minusMinutes(10)));
		assertThat(activities.get(0).getEndTimeAsZonedDateTime(), equalTo(now.minusMinutes(2)));
		assertThat(activities.get(1).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(1).getStartTimeAsZonedDateTime(), equalTo(now.minusMinutes(7)));
		assertThat(activities.get(1).getEndTimeAsZonedDateTime(), equalTo(now.minusMinutes(5)));
		assertThat(activities.get(2).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(2).getStartTimeAsZonedDateTime(), equalTo(now));
		assertThat(activities.get(2).getEndTimeAsZonedDateTime(), equalTo(now));

		ArgumentCaptor<ILoggingEvent> logEventCaptor = ArgumentCaptor.forClass(ILoggingEvent.class);
		verify(mockLogAppender).doAppend(logEventCaptor.capture());
		assertThat(logEventCaptor.getValue().getLevel(), equalTo(Level.WARN));
		assertThat(logEventCaptor.getValue().getFormattedMessage(), equalTo(expectedWarnMessage));
	}

	@Test
	public void analyze_appActivityOverlappingLastCachedActivityBeginAndEnd_lastCachedActivityUpdatedButNoNewGoalConflictMessagesCreated()
	{
		ZonedDateTime now = now();
		ZonedDateTime existingActivityStartTime = now.minusMinutes(5);
		ZonedDateTime existingActivityEndTime = now.minusSeconds(15);

		mockExistingActivity(gamblingGoal, existingActivityStartTime, existingActivityEndTime, "Poker App");

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = now;

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		verifyNoGoalConflictMessagesCreated();

		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());

		// Verify that the day was updated
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonEntity);
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity created", dayActivities.size(), equalTo(1));
		DayActivity dayActivity = dayActivities.get(0);

		// Verify that the activity was updated in the cache, because the end time is after the existing cached item
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());

		assertThat("Expect right activity start time", dayActivity.getLastActivity(deviceAnonId).getStartTime(),
				equalTo(startTime.toLocalDateTime()));
		assertThat("Expect right activity end time", dayActivity.getLastActivity(deviceAnonId).getEndTime(),
				equalTo(endTime.toLocalDateTime()));
	}

	@Test
	public void analyze_appActivityOverlappingLastCachedActivityBeginOnly_lastCachedActivityUpdatedButNoNewGoalConflictMessagesCreated()
	{
		ZonedDateTime now = now();
		ZonedDateTime existingActivityStartTime = now.minusMinutes(5);
		ZonedDateTime existingActivityEndTime = now.minusSeconds(15);

		mockExistingActivity(gamblingGoal, existingActivityStartTime, existingActivityEndTime, "Poker App");

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = existingActivityEndTime.minusMinutes(2);

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		verifyNoGoalConflictMessagesCreated();

		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());

		// Verify that the day was updated
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonEntity);
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity created", dayActivities.size(), equalTo(1));
		DayActivity dayActivity = dayActivities.get(0);

		// Verify that the activity was updated in the cache, because the end time is after the existing cached item
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());

		assertThat("Expect right activity start time", dayActivity.getLastActivity(deviceAnonId).getStartTime(),
				equalTo(startTime.toLocalDateTime()));
		assertThat("Expect right activity end time", dayActivity.getLastActivity(deviceAnonId).getEndTime(),
				equalTo(existingActivityEndTime.toLocalDateTime()));
	}

	@Test
	public void analyze_appActivityPreviousDayPrecedingCachedDayActivity_newDayActivityButNoNewGoalConflictMessageCreated()
	{
		ZonedDateTime now = now();
		ZonedDateTime today = now.truncatedTo(ChronoUnit.DAYS);
		ZonedDateTime yesterdayTime = now.minusDays(1);
		ZonedDateTime yesterday = yesterdayTime.truncatedTo(ChronoUnit.DAYS);

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now);

		ZonedDateTime startTime = yesterdayTime;
		ZonedDateTime endTime = yesterdayTime.plusMinutes(10);

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		verifyNoGoalConflictMessagesCreated();

		// Verify that a database lookup was done for yesterday
		verify(mockDayActivityRepository, atLeastOnce()).findOne(userAnonId, yesterdayTime.toLocalDate(), gamblingGoal.getId());
		// Verify that yesterday was inserted in the database
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

		// Double check today's activity
		List<Activity> todaysActivities = todaysDayActivity.getActivities();
		assertThat("One activity created for yesterday", todaysActivities.size(), equalTo(1));
		Activity todaysActivity = todaysActivities.get(0);
		assertThat("Expect right goal set to yesterday's activity", todaysActivity.getActivityCategory(),
				equalTo(gamblingGoal.getActivityCategory()));
		assertThat("Expect right date set to today's activity", todaysDayActivity.getStartDate(), equalTo(today.toLocalDate()));

		// Verify one activity was created, with the right goal
		List<Activity> activities = yesterdaysDayActivity.getActivities();
		assertThat("One activity created", activities.size(), equalTo(1));
		Activity activity = activities.get(0);
		assertThat("Expect right goal set to activity", activity.getActivityCategory(),
				equalTo(gamblingGoal.getActivityCategory()));

		assertThat("Expect new day", yesterdaysDayActivity, not(equalTo(existingDayActivity)));
		assertThat("Expect right date", yesterdaysDayActivity.getStartDate(), equalTo(yesterday.toLocalDate()));
		assertThat("Expect activity added", yesterdaysDayActivity.getLastActivity(deviceAnonId), notNullValue());
		assertThat("Expect matching start time", yesterdaysDayActivity.getLastActivity(deviceAnonId).getStartTime(),
				equalTo(startTime.toLocalDateTime()));
		assertThat("Expect matching end time", yesterdaysDayActivity.getLastActivity(deviceAnonId).getEndTime(),
				equalTo(endTime.toLocalDateTime()));

		// Verify that the activity was not updated in the cache, because the end time is before the existing cached item
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
				eq(gamblingGoal.getId()), any());
	}

	@Test
	public void analyze_crossDayAppActivity_twoDayActivitiesCreated()
	{
		ZonedDateTime startTime = now().minusDays(1);
		ZonedDateTime endTime = now();

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Poker App", startTime, endTime));

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
		assertThat(yesterdaysDayActivity.getStartDate(), equalTo(LocalDate.now().minusDays(1)));
		assertThat(todaysDayActivity.getStartDate(), equalTo(LocalDate.now()));

		Activity firstPart = yesterdaysDayActivity.getLastActivity(deviceAnonId);
		Activity nextPart = todaysDayActivity.getLastActivity(deviceAnonId);

		assertThat(firstPart.getStartTime(), equalTo(startTime.toLocalDateTime()));
		ZonedDateTime lastMidnight = now().truncatedTo(ChronoUnit.DAYS);
		assertThat(firstPart.getEndTime(), equalTo(lastMidnight.toLocalDateTime()));
		assertThat(nextPart.getStartTime(), equalTo(lastMidnight.toLocalDateTime()));
		assertThat(nextPart.getEndTime(), equalTo(endTime.toLocalDateTime()));
	}

	@Test
	public void analyze_appActivityAfterNetworkActivityWithinConflictInterval_newActivityRecordCreatedButNoNewGoalConflictMessagesCreated()
	{
		ZonedDateTime now = now();
		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now());

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Lotto App", now.minusMinutes(4), now.minusMinutes(2)));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(2));
		assertThat(activities.get(1).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(1).getDurationMinutes(), equalTo(2));
	}

	@Test
	public void analyze_networkActivityAfterAppActivityWithinConflictInterval_newActivityRecordCreatedButNoNewGoalConflictMessagesCreated()
	{
		ZonedDateTime now = now();
		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now.minusMinutes(10), now.minusMinutes(5),
				"Lotto App");

		service.analyze(userAnonId, createNetworkActivityForCategories("lotto"));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(2));
		assertThat(activities.get(1).getApp(), equalTo(Optional.empty()));
		assertThat(activities.get(1).getDurationMinutes(), equalTo(1));
	}

	@Test
	public void analyze_appActivityDifferentAppWithinConflictInterval_newActivityRecordCreatedButNoNewGoalConflictMessagesCreated()
	{
		ZonedDateTime now = now();
		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now.minusMinutes(10), now.minusMinutes(5),
				"Poker App");

		service.analyze(userAnonId, deviceAnonId, createSingleAppActivity("Lotto App", now.minusMinutes(4), now.minusMinutes(2)));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(2));
		assertThat(activities.get(1).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(1).getDurationMinutes(), equalTo(2));
	}

	@Test
	public void analyze_appActivitySameAppWithinConflictIntervalContinuous_activityRecordMergedAndNoNewGoalConflictMessageCreated()
	{
		ZonedDateTime now = now();
		ZonedDateTime existingActivityEndTime = now.minusMinutes(5);
		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now.minusMinutes(10), existingActivityEndTime,
				"Lotto App");

		service.analyze(userAnonId, deviceAnonId,
				createSingleAppActivity("Lotto App", existingActivityEndTime, now.minusMinutes(2)));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(1));
		assertThat(activities.get(0).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(0).getDurationMinutes(), equalTo(8));
	}

	@Test
	public void analyze_appActivitySameAppOverlappingLastCachedActivityEndTime_activityRecordMergedAndNoNewGoalConflictMessageCreated()
	{
		ZonedDateTime now = now();
		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now.minusMinutes(10), now.minusMinutes(5),
				"Lotto App");

		service.analyze(userAnonId, deviceAnonId,
				createSingleAppActivity("Lotto App", now.minusMinutes(5).minusSeconds(30), now.minusMinutes(2)));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(1));
		assertThat(activities.get(0).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(0).getDurationMinutes(), equalTo(8));
	}

	@Test
	public void analyze_appActivitySameAppWithinConflictIntervalButNotContinuous_activityRecordNotMergedButNoNewGoalConflictMessageCreated()
	{
		ZonedDateTime now = now();
		ZonedDateTime existingActivityEndTime = now.minusMinutes(5);
		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now.minusMinutes(10), now.minusMinutes(5),
				"Lotto App");

		service.analyze(userAnonId, deviceAnonId,
				createSingleAppActivity("Lotto App", existingActivityEndTime.plusSeconds(1), now.minusMinutes(2)));

		verifyNoGoalConflictMessagesCreated();
		List<Activity> activities = existingDayActivity.getActivities();
		assertThat(activities.size(), equalTo(2));
		assertThat(activities.get(1).getApp(), equalTo(Optional.of("Lotto App")));
		assertThat(activities.get(1).getDurationMinutes(), equalTo(2));
	}

	private NetworkActivityDto createNetworkActivityForCategories(String... conflictCategories)
	{
		return new NetworkActivityDto(new HashSet<>(Arrays.asList(conflictCategories)),
				"http://localhost/test" + new Random().nextInt(), Optional.empty());
	}

	private NetworkActivityDto createNetworkActivityForCategories(ZonedDateTime time, String... conflictCategories)
	{
		return new NetworkActivityDto(new HashSet<>(Arrays.asList(conflictCategories)),
				"http://localhost/test" + new Random().nextInt(), Optional.of(time));
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

	private List<Activity> verifyActivityUpdate(Goal... forGoals)
	{
		List<Activity> resultActivities = new ArrayList<>();
		// Verify there is a an activity in a day activity inside a week activity
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonEntity);
		for (Goal forGoal : forGoals)
		{
			List<WeekActivity> weekActivities = forGoal.getWeekActivities();
			assertThat("One week activity created", weekActivities.size(), equalTo(1));
			List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
			assertThat("One day activity created", dayActivities.size(), equalTo(1));
			List<Activity> activities = dayActivities.get(0).getActivities();
			assertThat("One activity created", activities.size(), equalTo(1));
			Activity activity = activities.get(0);
			assertThat("Expect right goal set to activity", activity.getActivityCategory(),
					equalTo(forGoal.getActivityCategory()));
			resultActivities.add(activity);

			// Verify that there is an activity cached
			verify(mockAnalysisEngineCacheService, atLeastOnce()).updateLastActivityForUser(eq(userAnonId), eq(deviceAnonId),
					eq(forGoal.getId()), any());
		}

		return resultActivities;
	}

	private void verifyNoActivityUpdate()
	{
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(any(), any(), any(), any());
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

	private AppActivityDto createSingleAppActivity(String app, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		AppActivityDto.Activity[] activities = { new AppActivityDto.Activity(app, startTime, endTime) };
		return new AppActivityDto(now(), activities);
	}

	private ZonedDateTime now()
	{
		return ZonedDateTime.now().withZoneSameInstant(userAnonZoneId);
	}
}