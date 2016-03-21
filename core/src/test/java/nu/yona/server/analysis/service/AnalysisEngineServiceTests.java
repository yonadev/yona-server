/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.CoreMatchers.equalTo;
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

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
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
	private Map<String, Goal> goalMap = new HashMap<String, Goal>();

	@Mock
	private ActivityCategoryService mockActivityCategoryService;
	@Mock
	private UserAnonymizedService mockUserAnonymizedService;
	@Mock
	private MessageService mockMessageService;
	@Mock
	private YonaProperties mockYonaProperties;
	@Mock
	private AnalysisEngineCacheService mockAnalysisEngineCacheService;
	@InjectMocks
	private AnalysisEngineService service = new AnalysisEngineService();

	private Goal gamblingGoal;
	private Goal newsGoal;
	private Goal gamingGoal;
	private Goal socialGoal;
	private Goal shoppingGoal;
	private MessageDestinationDTO anonMessageDestination;
	private UUID userAnonID;
	private UserAnonymized userAnonEntity;

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

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		anonMessageDestination = new MessageDestinationDTO(anonMessageDestinationEntity.getID());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal));
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		UserAnonymizedDTO userAnon = UserAnonymizedDTO.createInstance(userAnonEntity);
		userAnonID = userAnon.getID();

		// Stub the UserAnonymizedRepository to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonID)).thenReturn(userAnon);
		when(mockUserAnonymizedService.getUserAnonymizedEntity(userAnonID)).thenReturn(userAnonEntity);
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
		p.setUpdateSkipWindow(1L);
		p.setConflictInterval(10L);
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, ZonedDateTime.now());
		Activity earlierActivity = Activity.createInstance(new Date(), new Date());
		dayActivity.addActivity(earlierActivity);
		when(mockAnalysisEngineCacheService.fetchDayActivityForUser(eq(userAnonID), eq(gamblingGoal.getID()), any()))
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
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test1"));

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
		Date t = new Date();
		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test"));

		// Verify that there is an activity update.
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService).updateDayActivityForUser(dayActivity.capture());
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
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test"));

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
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test"));

		// Verify that there are 2 activities updated, for both goals.
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService, times(2)).updateDayActivityForUser(dayActivity.capture());
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
		p.setUpdateSkipWindow(0L);
		when(mockYonaProperties.getAnalysisService()).thenReturn(p);

		Date t = new Date();
		DayActivity dayActivity = DayActivity.createInstance(userAnonEntity, gamblingGoal, ZonedDateTime.now());
		Activity earlierActivity = Activity.createInstance(t, t);
		dayActivity.addActivity(earlierActivity);
		when(mockAnalysisEngineCacheService.fetchDayActivityForUser(eq(userAnonID), eq(gamblingGoal.getID()), any()))
				.thenReturn(dayActivity);

		// Execute the analysis engine service.
		Set<String> conflictCategories1 = new HashSet<String>(Arrays.asList("lotto"));
		Set<String> conflictCategories2 = new HashSet<String>(Arrays.asList("poker"));
		Set<String> conflictCategoriesNotMatching1 = new HashSet<String>(Arrays.asList("refdag"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategoriesNotMatching1, "http://localhost/test"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories1, "http://localhost/test1"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories2, "http://localhost/test2"));
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategoriesNotMatching1, "http://localhost/test3"));
		Date t2 = new Date();
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories2, "http://localhost/test4"));

		// Verify that there is no new conflict message sent.
		verify(mockMessageService, never()).sendMessage(any(), eq(anonMessageDestination));
		// Verify that the existing activity was updated in the cache.
		verify(mockAnalysisEngineCacheService, times(3)).updateDayActivityForUser(dayActivity);
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
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test"));

		// Verify that there was no attempted activity update.
		verify(mockAnalysisEngineCacheService, never()).fetchDayActivityForUser(eq(userAnonID), any(), any());
		verify(mockAnalysisEngineCacheService, never()).updateDayActivityForUser(any());

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
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test"));

		verifyActivityUpdate(shoppingGoal);
		verifyNoMessagesCreated();
	}

	private void verifyActivityUpdate(Goal forGoal)
	{
		// Verify that there is an activity update.
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService).updateDayActivityForUser(dayActivity.capture());
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
		service.analyze(new NetworkActivityDTO(userAnonID, conflictCategories, "http://localhost/test"));

		verifyActivityUpdate(socialGoal);
		verifyNoMessagesCreated();
	}
}