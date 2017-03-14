/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
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

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
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
public class AnalysisEngineServiceTests
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
	private DayActivityRepository mockDayActivityRepository;
	@Mock
	private WeekActivityRepository mockWeekActivityRepository;
	@Mock
	private LockPool<UUID> userAnonymizedSynchronizer;
	@Mock
	private TransactionHelper transactionHelper;

	@InjectMocks
	private final AnalysisEngineService service = new AnalysisEngineService();

	private Goal gamblingGoal;
	private Goal newsGoal;
	private Goal gamingGoal;
	private Goal socialGoal;
	private Goal shoppingGoal;
	private MessageDestinationDto anonMessageDestination;
	private UUID userAnonId;
	private UserAnonymized userAnonEntity;

	private ZoneId userAnonZoneId;

	@Before
	public void setUp()
	{
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
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		UserAnonymizedDto userAnon = UserAnonymizedDto.createInstance(userAnonEntity);
		anonMessageDestination = userAnon.getAnonymousDestination();
		userAnonId = userAnon.getId();
		userAnonZoneId = userAnon.getTimeZone();

		// Stub the UserAnonymizedService to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonId)).thenReturn(userAnon);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonId)).thenReturn(userAnonEntity);

		// Stub the GoalService to return our goals.
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, gamblingGoal.getId())).thenReturn(gamblingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedId(userAnonId, newsGoal.getId())).thenReturn(newsGoal);
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

	/*
	 * Tests the method to get all relevant categories.
	 */
	@Test
	public void getRelevantSmoothwallCategories()
	{
		assertThat(service.getRelevantSmoothwallCategories(),
				containsInAnyOrder("poker", "lotto", "refdag", "bbc", "games", "social", "webshop"));
	}

	/*
	 * Tests that a message is sent when another conflict occurs after the interval
	 */
	@Test
	public void messageSentWhenSecondConflictAfterConflictInterval()
	{
		// Normally there is one conflict message sent.
		// Set a short conflict interval such that the conflict messages are not aggregated.
		AnalysisServiceProperties p = new AnalysisServiceProperties();
		p.setUpdateSkipWindow("PT0.001S");
		p.setConflictInterval("PT0.01S");
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		mockExistingActivity(gamblingGoal, nowInAmsterdam());

		// Execute the analysis engine service after a period of inactivity longer than the conflict interval.

		try
		{
			Thread.sleep(11L);
		}
		catch (InterruptedException e)
		{

		}

		Set<String> conflictCategories = new HashSet<>(Arrays.asList("lotto"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test1", Optional.empty()));

		// Verify that there is a new conflict message sent.
		verifyGoalConflictMessageSent(gamblingGoal);

		// Restore default properties.
		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());
	}

	/*
	 * Tests that a second conflict within the interval does not cause a message to be sent
	 */
	@Test
	public void noMessageSentWhenSecondConflictWithinConflictInterval()
	{
		mockExistingActivity(gamblingGoal, nowInAmsterdam());

		Set<String> conflictCategories = new HashSet<>(Arrays.asList("lotto"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test1", Optional.empty()));

		// Verify that there is no new conflict message sent.
		verifyNoMessagesCreated();
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a matching category.
	 */
	@Test
	public void messageCreatedOnMatch()
	{
		ZonedDateTime t = nowInAmsterdam();
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("lotto"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify there is a an activity in a day activity inside a week activity
		ArgumentCaptor<UserAnonymized> userAnonymizedCaptor = ArgumentCaptor.forClass(UserAnonymized.class);
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonymizedCaptor.capture());
		UserAnonymized userAnonymized = userAnonymizedCaptor.getValue();
		assertThat("Expect right user set to activity", userAnonymized, equalTo(userAnonEntity));
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity created", dayActivities.size(), equalTo(1));
		List<Activity> activities = dayActivities.get(0).getActivities();
		assertThat("One activity created", activities.size(), equalTo(1));
		Activity activity = activities.get(0);
		assertThat("Expect start time set just about time of analysis", activity.getStartTimeAsZonedDateTime(),
				greaterThanOrEqualTo(t));
		assertThat("Expect end time set just about time of analysis", activity.getEndTimeAsZonedDateTime(),
				greaterThanOrEqualTo(t));
		assertThat("Expect right goal set to activity", activity.getActivityCategory(),
				equalTo(gamblingGoal.getActivityCategory()));

		// Verify that there is an activity cached
		verify(mockAnalysisEngineCacheService).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()), any());

		verifyGoalConflictMessageSent(gamblingGoal);
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a not matching and a matching category.
	 */
	@Test
	public void messageCreatedOnMatchOneCategoryOfMultiple()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("refdag", "lotto"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(gamblingGoal);
		verifyGoalConflictMessageSent(gamblingGoal);
	}

	/**
	 * Tests that multiple conflict messages are created when analysis service is called with multiple matching categories.
	 */
	@Test
	public void messagesCreatedOnMatchMultiple()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("lotto", "games"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(gamblingGoal, gamingGoal);
		verifyGoalConflictMessageSent(gamblingGoal, gamingGoal);
	}

	/**
	 * Tests that a conflict message is updated when analysis service is called with a matching category after a short time.
	 */
	@Test
	public void messageAggregation()
	{
		// Normally there is one conflict message sent.
		// Set update skip window to 0 such that the conflict messages are aggregated immediately.
		AnalysisServiceProperties p = new AnalysisServiceProperties();
		p.setUpdateSkipWindow("PT0S");
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		ZonedDateTime t = nowInAmsterdam();
		DayActivity dayActivity = mockExistingActivity(gamblingGoal, t);
		Activity existingActivityEntity = dayActivity.getLastActivity();

		// Execute the analysis engine service.
		Set<String> conflictCategories1 = new HashSet<>(Arrays.asList("lotto"));
		Set<String> conflictCategories2 = new HashSet<>(Arrays.asList("poker"));
		Set<String> conflictCategoriesNotMatching1 = new HashSet<>(Arrays.asList("refdag"));
		service.analyze(userAnonId,
				new NetworkActivityDto(conflictCategoriesNotMatching1, "http://localhost/test", Optional.empty()));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories1, "http://localhost/test1", Optional.empty()));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories2, "http://localhost/test2", Optional.empty()));
		service.analyze(userAnonId,
				new NetworkActivityDto(conflictCategoriesNotMatching1, "http://localhost/test3", Optional.empty()));
		ZonedDateTime t2 = nowInAmsterdam();
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories2, "http://localhost/test4", Optional.empty()));

		// Verify that the cache is used to check existing activity
		verify(mockAnalysisEngineCacheService, times(3)).fetchLastActivityForUser(userAnonId, gamblingGoal.getId());
		// Verify that there is no new conflict message sent.
		verifyNoMessagesCreated();
		// Verify that the existing day activity was updated.
		verifyActivityUpdate(gamblingGoal);
		// Verify that the existing activity was updated in the cache.
		verify(mockAnalysisEngineCacheService, times(3)).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());
		assertThat("Expect start time to remain the same", existingActivityEntity.getStartTime(), equalTo(t.toLocalDateTime()));
		assertThat("Expect end time updated at the last executed analysis matching the goals",
				existingActivityEntity.getEndTimeAsZonedDateTime(), greaterThanOrEqualTo(t2));

		// Restore default properties.
		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnNoMatch()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("refdag"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify that there was no attempted activity update.
		verify(mockAnalysisEngineCacheService, never()).fetchLastActivityForUser(eq(userAnonId), any());
		verify(mockDayActivityRepository, never()).save(any(DayActivity.class));
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(any(), any(), any());

		verifyNoMessagesCreated();
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnTimeZoneGoal()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("webshop"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(shoppingGoal);
		verifyNoMessagesCreated();
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnNonZeroBudgetGoal()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<>(Arrays.asList("social"));
		service.analyze(userAnonId, new NetworkActivityDto(conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(socialGoal);
		verifyNoMessagesCreated();
	}

	@Test
	public void activityOnNewDay()
	{
		ZonedDateTime today = ZonedDateTime.now(userAnonZoneId).truncatedTo(ChronoUnit.DAYS);
		// mock earlier activity at yesterday 23:59:58,
		// add new activity at today 00:00:01
		ZonedDateTime existingActivityTime = today.minusDays(1).withHour(23).withMinute(59).withSecond(58);

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, existingActivityTime);

		ZonedDateTime startTime = today.withHour(0).withMinute(0).withSecond(1);
		ZonedDateTime endTime = today.withHour(0).withMinute(10);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verifyGoalConflictMessageSent(gamblingGoal);

		// Verify there are now two day activities
		ArgumentCaptor<UserAnonymized> userAnonymizedCaptor = ArgumentCaptor.forClass(UserAnonymized.class);
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonymizedCaptor.capture());
		UserAnonymized userAnonymized = userAnonymizedCaptor.getValue();
		assertThat("Expect right user set to activity", userAnonymized, equalTo(userAnonEntity));
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
		assertThat("Expect activity added", todaysDayActivity.getLastActivity(), notNullValue());
		assertThat("Expect matching start time", todaysDayActivity.getLastActivity().getStartTime(),
				equalTo(startTime.toLocalDateTime()));
		assertThat("Expect matching end time", todaysDayActivity.getLastActivity().getEndTime(),
				equalTo(endTime.toLocalDateTime()));

		// Verify that there is an activity cached
		verify(mockAnalysisEngineCacheService, atLeastOnce()).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());

	}

	@Test
	public void appActivityCompletelyPrecedingLastCachedActivity()
	{
		ZonedDateTime now = ZonedDateTime.now(userAnonZoneId);
		ZonedDateTime existingActivityTime = now;

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, existingActivityTime);
		Activity existingActivity = existingDayActivity.getLastActivity();

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = now.minusSeconds(15);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verifyGoalConflictMessageSent(gamblingGoal);

		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());

		// Verify that the day was updated
		ArgumentCaptor<UserAnonymized> userAnonymizedCaptor = ArgumentCaptor.forClass(UserAnonymized.class);
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonymizedCaptor.capture());
		UserAnonymized userAnonymized = userAnonymizedCaptor.getValue();
		assertThat("Expect right user set to activity", userAnonymized, equalTo(userAnonEntity));
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity created", dayActivities.size(), equalTo(1));
		DayActivity dayActivity = dayActivities.get(0);

		// Verify that the activity was not updated in the cache, because the end time is before the existing cached item
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());

		assertThat("Expect a new activity added (merge is quite complicated so has not been implemented yet)",
				dayActivity.getLastActivity(), not(equalTo(existingActivity)));
		assertThat("Expect right activity start time", dayActivity.getLastActivity().getStartTime(),
				equalTo(startTime.toLocalDateTime()));
	}

	@Test
	public void appActivityPrecedingAndExtendingLastCachedActivity()
	{
		ZonedDateTime now = ZonedDateTime.now(userAnonZoneId);
		ZonedDateTime existingActivityTime = now.minusSeconds(15);

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, existingActivityTime);
		Activity existingActivity = existingDayActivity.getLastActivity();

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = now;
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verifyGoalConflictMessageSent(gamblingGoal);

		// Verify that a database lookup was done finding the existing DayActivity to update
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, now.toLocalDate(), gamblingGoal.getId());

		// Verify that the day was updated
		ArgumentCaptor<UserAnonymized> userAnonymizedCaptor = ArgumentCaptor.forClass(UserAnonymized.class);
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonymizedCaptor.capture());
		UserAnonymized userAnonymized = userAnonymizedCaptor.getValue();
		assertThat("Expect right user set to activity", userAnonymized, equalTo(userAnonEntity));
		List<WeekActivity> weekActivities = gamblingGoal.getWeekActivities();
		assertThat("One week activity created", weekActivities.size(), equalTo(1));
		List<DayActivity> dayActivities = weekActivities.get(0).getDayActivities();
		assertThat("One day activity created", dayActivities.size(), equalTo(1));
		DayActivity dayActivity = dayActivities.get(0);

		// Verify that the activity was updated in the cache, because the end time is after the existing cached item
		verify(mockAnalysisEngineCacheService, times(1)).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());

		assertThat("Expect a new activity added (merge is quite complicated so has not been implemented yet)",
				dayActivity.getLastActivity(), not(equalTo(existingActivity)));
		assertThat("Expect right activity start time", dayActivity.getLastActivity().getStartTime(),
				equalTo(startTime.toLocalDateTime()));
	}

	@Test
	public void appActivityPrecedingCachedDayActivity()
	{
		ZonedDateTime now = ZonedDateTime.now(userAnonZoneId);
		ZonedDateTime today = now.truncatedTo(ChronoUnit.DAYS);
		ZonedDateTime yesterdayTime = now.minusDays(1);
		ZonedDateTime yesterday = yesterdayTime.truncatedTo(ChronoUnit.DAYS);

		DayActivity existingDayActivity = mockExistingActivity(gamblingGoal, now);

		ZonedDateTime startTime = yesterdayTime;
		ZonedDateTime endTime = yesterdayTime.plusMinutes(10);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verifyGoalConflictMessageSent(gamblingGoal);

		// Verify that a database lookup was done for yesterday
		verify(mockDayActivityRepository, times(1)).findOne(userAnonId, yesterdayTime.toLocalDate(), gamblingGoal.getId());
		// Verify that yesterday was inserted in the database
		ArgumentCaptor<UserAnonymized> userAnonymizedCaptor = ArgumentCaptor.forClass(UserAnonymized.class);
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonymizedCaptor.capture());
		UserAnonymized userAnonymized = userAnonymizedCaptor.getValue();
		assertThat("Expect right user set to activity", userAnonymized, equalTo(userAnonEntity));
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
		assertThat("Expect activity added", yesterdaysDayActivity.getLastActivity(), notNullValue());
		assertThat("Expect matching start time", yesterdaysDayActivity.getLastActivity().getStartTime(),
				equalTo(startTime.toLocalDateTime()));
		assertThat("Expect matching end time", yesterdaysDayActivity.getLastActivity().getEndTime(),
				equalTo(endTime.toLocalDateTime()));

		// Verify that the activity was not updated in the cache, because the end time is before the existing cached item
		verify(mockAnalysisEngineCacheService, never()).updateLastActivityForUser(eq(userAnonId), eq(gamblingGoal.getId()),
				any());
	}

	@Test
	public void crossDayActivity()
	{
		ZonedDateTime startTime = ZonedDateTime.now(userAnonZoneId).minusDays(1);
		ZonedDateTime endTime = ZonedDateTime.now(userAnonZoneId);
		service.analyze(userAnonId, createSingleAppActivity("Poker App", startTime, endTime));

		ArgumentCaptor<UserAnonymized> userAnonymizedCaptor = ArgumentCaptor.forClass(UserAnonymized.class);
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonymizedCaptor.capture());
		UserAnonymized userAnonymized = userAnonymizedCaptor.getValue();
		assertThat("Expect right user set to activity", userAnonymized, equalTo(userAnonEntity));
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

		Activity firstPart = yesterdaysDayActivity.getLastActivity();
		Activity nextPart = todaysDayActivity.getLastActivity();

		assertThat(firstPart.getStartTime(), equalTo(startTime.toLocalDateTime()));
		ZonedDateTime lastMidnight = ZonedDateTime.now(userAnonZoneId).truncatedTo(ChronoUnit.DAYS);
		assertThat(firstPart.getEndTime(), equalTo(lastMidnight.minusSeconds(1).toLocalDateTime()));
		assertThat(nextPart.getStartTime(), equalTo(lastMidnight.toLocalDateTime()));
		assertThat(nextPart.getEndTime(), equalTo(endTime.toLocalDateTime()));
	}

	private void verifyGoalConflictMessageSent(Goal... forGoals)
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

	private void verifyActivityUpdate(Goal... forGoals)
	{
		// Verify there is a an activity in a day activity inside a week activity
		ArgumentCaptor<UserAnonymized> userAnonymizedCaptor = ArgumentCaptor.forClass(UserAnonymized.class);
		verify(mockUserAnonymizedService, atLeastOnce()).updateUserAnonymized(userAnonymizedCaptor.capture());
		UserAnonymized userAnonymized = userAnonymizedCaptor.getValue();
		assertThat("Expect right user set to activity", userAnonymized, equalTo(userAnonEntity));
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

			// Verify that there is an activity cached
			verify(mockAnalysisEngineCacheService, atLeastOnce()).updateLastActivityForUser(eq(userAnonId), eq(forGoal.getId()),
					any());
		}
	}

	private void verifyNoMessagesCreated()
	{
		verify(mockMessageService, never()).sendMessageAndFlushToDatabase(any(), eq(anonMessageDestination));
	}

	private DayActivity mockExistingActivity(Goal forGoal, ZonedDateTime activityTime)
	{
		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, forGoal, activityTime.getZone(),
				activityTime.truncatedTo(ChronoUnit.DAYS).toLocalDate());
		Activity existingActivityEntity = Activity.createInstance(activityTime.getZone(), activityTime.toLocalDateTime(),
				activityTime.toLocalDateTime());
		dayActivity.addActivity(existingActivityEntity);
		ActivityDto existingActivity = ActivityDto.createInstance(existingActivityEntity);
		when(mockDayActivityRepository.findOne(userAnonId, dayActivity.getStartDate(), forGoal.getId())).thenReturn(dayActivity);
		when(mockAnalysisEngineCacheService.fetchLastActivityForUser(userAnonId, forGoal.getId())).thenReturn(existingActivity);
		WeekActivity weekActivity = WeekActivity.createInstance(userAnonEntity, forGoal, activityTime.getZone(),
				TimeUtil.getStartOfWeek(activityTime.toLocalDate()));
		weekActivity.addDayActivity(dayActivity);
		forGoal.addWeekActivity(weekActivity);
		return dayActivity;
	}

	private AppActivityDto createSingleAppActivity(String app, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		AppActivityDto.Activity[] activities = { new AppActivityDto.Activity(app, startTime, endTime) };
		return new AppActivityDto(nowInAmsterdam(), activities);
	}

	private ZonedDateTime nowInAmsterdam()
	{
		return ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Europe/Amsterdam"));
	}
}