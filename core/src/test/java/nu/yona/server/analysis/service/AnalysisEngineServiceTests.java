/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.AnalysisServiceProperties;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;

@RunWith(MockitoJUnitRunner.class)
public class AnalysisEngineServiceTests
{
	private final Map<String, Goal> goalMap = new HashMap<String, Goal>();

	@Mock
	private ActivityCategoryService mockActivityCategoryService;
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
	@InjectMocks
	private final AnalysisEngineService service = new AnalysisEngineService();

	private Goal gamblingGoal;
	private Goal newsGoal;
	private Goal gamingGoal;
	private Goal socialGoal;
	private Goal shoppingGoal;
	private MessageDestinationDTO anonMessageDestination;
	private UUID userAnonID;
	private UserAnonymized userAnonEntity;

	private ZoneId userAnonZoneId;

	@Before
	public void setUp()
	{
		gamblingGoal = BudgetGoal.createNoGoInstance(ZonedDateTime.now(),
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
						new HashSet<String>(Arrays.asList("poker", "lotto")),
						new HashSet<String>(Arrays.asList("Poker App", "Lotto App"))));
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

		when(mockActivityCategoryService.getAllActivityCategories()).thenReturn(getAllActivityCategories());
		when(mockActivityCategoryService.getMatchingCategoriesForSmoothwallCategories(anySetOf(String.class)))
				.thenAnswer(new Answer<Set<ActivityCategoryDTO>>() {
					@Override
					public Set<ActivityCategoryDTO> answer(InvocationOnMock invocation) throws Throwable
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
		when(mockActivityCategoryService.getMatchingCategoriesForApp(any(String.class)))
				.thenAnswer(new Answer<Set<ActivityCategoryDTO>>() {
					@Override
					public Set<ActivityCategoryDTO> answer(InvocationOnMock invocation) throws Throwable
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
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal));
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		UserAnonymizedDTO userAnon = UserAnonymizedDTO.createInstance(userAnonEntity);
		anonMessageDestination = userAnon.getAnonymousDestination();
		userAnonID = userAnon.getID();
		userAnonZoneId = ZoneId.of(userAnon.getTimeZoneId());

		// Stub the UserAnonymizedService to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonID)).thenReturn(userAnon);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonID)).thenReturn(userAnonEntity);

		// Stub the GoalService to return our goals.
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, gamblingGoal.getID())).thenReturn(gamblingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, newsGoal.getID())).thenReturn(newsGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, gamingGoal.getID())).thenReturn(gamingGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, socialGoal.getID())).thenReturn(socialGoal);
		when(mockGoalService.getGoalEntityForUserAnonymizedID(userAnonID, shoppingGoal.getID())).thenReturn(shoppingGoal);
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	private Set<ActivityCategoryDTO> getAllActivityCategories()
	{
		return goalMap.values().stream().map(goal -> ActivityCategoryDTO.createInstance(goal.getActivityCategory()))
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
	 * Tests that two conflict messages are generated when the conflict interval is passed.
	 */
	@Test
	public void conflictInterval()
	{
		// Normally there is one conflict message sent.
		// Set a short conflict interval such that the conflict messages are not aggregated.
		AnalysisServiceProperties p = new AnalysisServiceProperties();
		p.setUpdateSkipWindow("PT0.001S");
		p.setConflictInterval("PT0.01S");
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal,
				ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS));
		Activity earlierActivity = Activity.createInstance(ZonedDateTime.now(), ZonedDateTime.now());
		dayActivity.addActivity(earlierActivity);
		when(mockAnalysisEngineCacheService.fetchLastDayActivityForUser(eq(userAnonID), eq(gamblingGoal.getID())))
				.thenReturn(dayActivity);

		// Execute the analysis engine service after a period of inactivity longer than the conflict interval.

		try
		{
			Thread.sleep(11L);
		}
		catch (InterruptedException e)
		{

		}

		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test1", Optional.empty()));

		// Verify that there is a new conflict message sent.
		ArgumentCaptor<MessageDestinationDTO> messageDestination = ArgumentCaptor.forClass(MessageDestinationDTO.class);
		verify(mockMessageService, times(1)).sendMessage(any(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getID(),
				equalTo(anonMessageDestination.getID()));

		// Restore default properties.
		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a matching category.
	 */
	@Test
	public void messageCreatedOnMatch()
	{
		ZonedDateTime t = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Europe/Amsterdam"));
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify that there is an activity update.
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService).updateLastDayActivityForUser(dayActivity.capture());
		Activity activity = dayActivity.getValue().getLastActivity();
		assertThat("Expect right user set to activity", dayActivity.getValue().getUserAnonymized(), equalTo(userAnonEntity));
		assertThat("Expect start time set just about time of analysis", activity.getStartTime(), greaterThanOrEqualTo(t));
		assertThat("Expect end time set just about time of analysis", activity.getEndTime(), greaterThanOrEqualTo(t));
		assertThat("Expect right goal set to activity", dayActivity.getValue().getGoal(), equalTo(gamblingGoal));
		// Verify that there is a new conflict message sent.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		ArgumentCaptor<MessageDestinationDTO> messageDestination = ArgumentCaptor.forClass(MessageDestinationDTO.class);
		verify(mockMessageService).sendMessage(message.capture(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getID(),
				equalTo(anonMessageDestination.getID()));
		assertThat("Expected right related user set to goal conflict message", message.getValue().getRelatedUserAnonymizedID(),
				equalTo(userAnonID));
		assertThat("Expected right goal set to goal conflict message", message.getValue().getGoal().getID(),
				equalTo(gamblingGoal.getID()));
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a not matching and a matching category.
	 */
	@Test
	public void messageCreatedOnMatchOneCategoryOfMultiple()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("refdag", "lotto"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(gamblingGoal);
		// Verify that there is a new conflict message sent.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		ArgumentCaptor<MessageDestinationDTO> messageDestination = ArgumentCaptor.forClass(MessageDestinationDTO.class);
		verify(mockMessageService).sendMessage(message.capture(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getID(),
				equalTo(anonMessageDestination.getID()));
		assertThat("Expect right goal set to goal conflict message", message.getValue().getGoal().getID(),
				equalTo(gamblingGoal.getID()));
	}

	/**
	 * Tests that multiple conflict messages are created when analysis service is called with multiple matching categories.
	 */
	@Test
	public void messagesCreatedOnMatchMultiple()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto", "games"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify that there are 2 activities updated, for both goals.
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService, times(2)).updateLastDayActivityForUser(dayActivity.capture());
		assertThat("Expect right goals set to activities",
				dayActivity.getAllValues().stream().map(a -> a.getGoal()).collect(Collectors.toSet()),
				containsInAnyOrder(gamblingGoal, gamingGoal));
		// Verify that there are 2 conflict messages sent, for both goals.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		ArgumentCaptor<MessageDestinationDTO> messageDestination = ArgumentCaptor.forClass(MessageDestinationDTO.class);
		verify(mockMessageService, times(2)).sendMessage(message.capture(), messageDestination.capture());
		assertThat("Expect right message destination", messageDestination.getValue().getID(),
				equalTo(anonMessageDestination.getID()));
		assertThat("Expect right goals set to goal conflict messages",
				message.getAllValues().stream().map(m -> m.getGoal()).collect(Collectors.toSet()),
				containsInAnyOrder(gamblingGoal, gamingGoal));
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

		ZonedDateTime t = ZonedDateTime.now();
		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal,
				ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS));
		Activity earlierActivity = Activity.createInstance(t, t);
		dayActivity.addActivity(earlierActivity);
		when(mockAnalysisEngineCacheService.fetchLastDayActivityForUser(eq(userAnonID), eq(gamblingGoal.getID())))
				.thenReturn(dayActivity);

		// Execute the analysis engine service.
		Set<String> conflictCategories1 = new HashSet<String>(Arrays.asList("lotto"));
		Set<String> conflictCategories2 = new HashSet<String>(Arrays.asList("poker"));
		Set<String> conflictCategoriesNotMatching1 = new HashSet<String>(Arrays.asList("refdag"));
		service.analyze(
				new NetworkActivityDTO(userAnonID, conflictCategoriesNotMatching1, "http://localhost/test", Optional.empty()));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories1, "http://localhost/test1", Optional.empty()));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories2, "http://localhost/test2", Optional.empty()));
		service.analyze(
				new NetworkActivityDTO(userAnonID, conflictCategoriesNotMatching1, "http://localhost/test3", Optional.empty()));
		ZonedDateTime t2 = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Europe/Amsterdam"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories2, "http://localhost/test4", Optional.empty()));

		// Verify that there is no new conflict message sent.
		verify(mockMessageService, never()).sendMessage(any(), eq(anonMessageDestination));
		// Verify that the existing activity was updated in the cache.
		verify(mockAnalysisEngineCacheService, times(3)).updateLastDayActivityForUser(dayActivity);
		assertThat("Expect start time to remain the same", earlierActivity.getStartTime(), equalTo(t));
		assertThat("Expect end time updated at the last executed analysis matching the goals", earlierActivity.getEndTime(),
				greaterThanOrEqualTo(t2));

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
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("refdag"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test", Optional.empty()));

		// Verify that there was no attempted activity update.
		verify(mockAnalysisEngineCacheService, never()).fetchLastDayActivityForUser(eq(userAnonID), any());
		verify(mockAnalysisEngineCacheService, never()).updateLastDayActivityForUser(any());

		verifyNoMessagesCreated();
	}

	private void verifyNoMessagesCreated()
	{
		// Verify that there is no conflict message sent.
		verify(mockMessageService, never()).sendMessage(any(), eq(anonMessageDestination));
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnTimeZoneGoal()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("webshop"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(shoppingGoal);
		verifyNoMessagesCreated();
	}

	private void verifyActivityUpdate(Goal forGoal)
	{
		// Verify that there is an activity update.
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService).updateLastDayActivityForUser(dayActivity.capture());
		assertThat("Expect right goal set to activity", dayActivity.getValue().getGoal(), equalTo(forGoal));
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnNonZeroBudgetGoal()
	{
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("social"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test", Optional.empty()));

		verifyActivityUpdate(socialGoal);
		verifyNoMessagesCreated();
	}

	@Test
	public void activityOnNewDay()
	{
		ZonedDateTime now = ZonedDateTime.of(2016, 4, 29, 20, 29, 1, 0, userAnonZoneId);

		DayActivity earlierDayActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal,
				now.minusDays(1).truncatedTo(ChronoUnit.DAYS));
		ZonedDateTime earlierActivityTime = now.minusDays(1);
		Activity earlierActivity = Activity.createInstance(earlierActivityTime, earlierActivityTime);
		earlierDayActivity.addActivity(earlierActivity);
		when(mockAnalysisEngineCacheService.fetchLastDayActivityForUser(eq(userAnonID), eq(gamblingGoal.getID())))
				.thenReturn(earlierDayActivity);

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = now;
		service.analyze(userAnonID, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verify(mockMessageService, times(1)).sendMessage(any(), eq(anonMessageDestination));
		// Verify that a new day was cached.
		ArgumentCaptor<DayActivity> newDayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService, times(1)).updateLastDayActivityForUser(newDayActivity.capture());
		assertThat("Expect new day", newDayActivity.getValue(), not(equalTo(earlierDayActivity)));
		assertThat("Expect right date", newDayActivity.getValue().getStartTime(), equalTo(now.truncatedTo(ChronoUnit.DAYS)));
		assertThat("Expect activity added", newDayActivity.getValue().getLastActivity(), notNullValue());
		assertThat("Expect matching start time", newDayActivity.getValue().getLastActivity().getStartTime(), equalTo(startTime));
		assertThat("Expect matching end time", newDayActivity.getValue().getLastActivity().getEndTime(), equalTo(endTime));
	}

	@Test
	public void appActivityPrecedingLastCachedActivity()
	{
		ZonedDateTime now = ZonedDateTime.of(2016, 4, 29, 20, 29, 1, 0, userAnonZoneId);

		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, now.truncatedTo(ChronoUnit.DAYS));
		ZonedDateTime earlierActivityTime = now;
		Activity earlierActivity = Activity.createInstance(earlierActivityTime, earlierActivityTime);
		dayActivity.addActivity(earlierActivity);
		when(mockAnalysisEngineCacheService.fetchLastDayActivityForUser(eq(userAnonID), eq(gamblingGoal.getID())))
				.thenReturn(dayActivity);

		ZonedDateTime startTime = now.minusMinutes(10);
		ZonedDateTime endTime = now;
		service.analyze(userAnonID, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verify(mockMessageService, times(1)).sendMessage(any(), eq(anonMessageDestination));
		// Verify that no database lookup was done
		verify(mockDayActivityRepository, never()).findOne(userAnonID, now.toLocalDate(), gamblingGoal.getID());
		// Verify that the day was updated in the cache
		verify(mockAnalysisEngineCacheService, times(1)).updateLastDayActivityForUser(dayActivity);

		assertThat("Expect a new activity added (merge is quite complicated so has not been implemented yet)",
				dayActivity.getLastActivity().getID(), not(equalTo(earlierActivity.getID())));
		assertThat("Expect right activity start time", dayActivity.getLastActivity().getStartTime(), equalTo(startTime));
	}

	@Test
	public void appActivityPrecedingCachedDayActivity()
	{
		ZonedDateTime now = ZonedDateTime.of(2016, 4, 29, 20, 29, 1, 0, userAnonZoneId);
		ZonedDateTime yesterdayTime = now.minusDays(1);

		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, now.truncatedTo(ChronoUnit.DAYS));
		ZonedDateTime earlierActivityTime = now;
		Activity earlierActivity = Activity.createInstance(earlierActivityTime, earlierActivityTime);
		dayActivity.addActivity(earlierActivity);
		when(mockAnalysisEngineCacheService.fetchLastDayActivityForUser(eq(userAnonID), eq(gamblingGoal.getID())))
				.thenReturn(dayActivity);

		ZonedDateTime startTime = yesterdayTime.minusMinutes(10);
		ZonedDateTime endTime = yesterdayTime;
		service.analyze(userAnonID, createSingleAppActivity("Poker App", startTime, endTime));

		// Verify that there is a new conflict message sent.
		verify(mockMessageService, times(1)).sendMessage(any(), eq(anonMessageDestination));
		// Verify that a database lookup was done for yesterday
		verify(mockDayActivityRepository, times(1)).findOne(userAnonID, yesterdayTime.toLocalDate(), gamblingGoal.getID());
		// Verify that yesterday was inserted in the database
		ArgumentCaptor<DayActivity> precedingDayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockDayActivityRepository, times(1)).save(precedingDayActivity.capture());
		assertThat("Expect right date", precedingDayActivity.getValue().getStartTime(),
				equalTo(yesterdayTime.truncatedTo(ChronoUnit.DAYS)));
		assertThat("Expect activity added", precedingDayActivity.getValue().getLastActivity(), notNullValue());
		// Verify that the day preceding the cached day was not cached!
		verify(mockAnalysisEngineCacheService, never()).updateLastDayActivityForUser(any());
	}

	@Test
	public void crossDayActivity()
	{
		ZonedDateTime startTime = ZonedDateTime.of(2016, 4, 5, 19, 39, 0, 0, userAnonZoneId);
		ZonedDateTime endTime = ZonedDateTime.of(2016, 4, 6, 1, 00, 0, 0, userAnonZoneId);
		service.analyze(userAnonID, createSingleAppActivity("Poker App", startTime, endTime));

		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService, times(2)).updateLastDayActivityForUser(dayActivity.capture());

		DayActivity firstDay = dayActivity.getAllValues().get(0);
		DayActivity nextDay = dayActivity.getAllValues().get(1);
		assertThat(firstDay.getDate(), equalTo(LocalDate.of(2016, 4, 5)));
		assertThat(nextDay.getDate(), equalTo(LocalDate.of(2016, 4, 6)));

		Activity firstPart = firstDay.getLastActivity();
		Activity nextPart = nextDay.getLastActivity();

		assertThat(firstPart.getStartTime(), equalTo(startTime));
		assertThat(firstPart.getEndTime(), equalTo(ZonedDateTime.of(2016, 4, 5, 23, 59, 59, 0, userAnonZoneId)));
		assertThat(nextPart.getStartTime(), equalTo(ZonedDateTime.of(2016, 4, 6, 0, 0, 0, 0, userAnonZoneId)));
		assertThat(nextPart.getEndTime(), equalTo(endTime));
	}

	private AppActivityDTO createSingleAppActivity(String app, ZonedDateTime startTime, ZonedDateTime endTime)
	{
		AppActivityDTO.Activity[] activities = { new AppActivityDTO.Activity(app, startTime, endTime) };
		return new AppActivityDTO(ZonedDateTime.now(), activities);
	}
}