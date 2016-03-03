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

import java.time.LocalDate;
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
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.AnalysisServiceProperties;
import nu.yona.server.properties.YonaProperties;
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
	private MessageDestinationDTO anonMessageDestination;
	private UUID userAnonID;

	@Before
	public void setUp()
	{
		gamblingGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance("gambling", false,
				new HashSet<String>(Arrays.asList("poker", "lotto")), Collections.emptySet()));
		newsGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance("news", false,
				new HashSet<String>(Arrays.asList("refdag", "bbc")), Collections.emptySet()));
		gamingGoal = BudgetGoal.createNoGoInstance(ActivityCategory.createInstance("gaming", false,
				new HashSet<String>(Arrays.asList("games")), Collections.emptySet()));

		goalMap.put("gambling", gamblingGoal);
		goalMap.put("news", newsGoal);
		goalMap.put("gaming", gamingGoal);

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
		anonMessageDestination = new MessageDestinationDTO(UUID.randomUUID());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(gamblingGoal, gamingGoal));
		UserAnonymizedDTO userAnon = new UserAnonymizedDTO(UUID.randomUUID(), goals, anonMessageDestination,
				Collections.emptySet());
		userAnonID = userAnon.getID();

		// Stub the UserAnonymizedRepository to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonID)).thenReturn(userAnon);
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
		assertThat(service.getRelevantSmoothwallCategories(), containsInAnyOrder("poker", "lotto", "refdag", "bbc", "games"));
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

		DayActivity dayActivity = DayActivity.createInstance(userAnonID, gamblingGoal, LocalDate.now());
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
		verify(mockMessageService, times(1)).sendMessage(any(), eq(anonMessageDestination));

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
		Activity activity = dayActivity.getValue().getLatestActivity();
		assertThat("Expect right user set to activity", dayActivity.getValue().getUserAnonymizedID(), equalTo(userAnonID));
		assertThat("Expect start time set just about time of analysis", activity.getStartTime(), greaterThanOrEqualTo(t));
		assertThat("Expect end time set just about time of analysis", activity.getEndTime(), greaterThanOrEqualTo(t));
		assertThat("Expect right goal set to activity", dayActivity.getValue().getGoalID(), equalTo(gamblingGoal.getID()));
		// Verify that there is a new conflict message sent.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		verify(mockMessageService).sendMessage(message.capture(), eq(anonMessageDestination));
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

		// Verify that there is an activity update.
		ArgumentCaptor<DayActivity> dayActivity = ArgumentCaptor.forClass(DayActivity.class);
		verify(mockAnalysisEngineCacheService).updateDayActivityForUser(dayActivity.capture());
		assertThat("Expect right goal set to activity", dayActivity.getValue().getGoalID(), equalTo(gamblingGoal.getID()));
		// Verify that there is a new conflict message sent.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		verify(mockMessageService).sendMessage(message.capture(), eq(anonMessageDestination));
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
				dayActivity.getAllValues().stream().map(a -> a.getGoalID()).collect(Collectors.toSet()),
				containsInAnyOrder(gamblingGoal.getID(), gamingGoal.getID()));
		// Verify that there are 2 conflict messages sent, for both goals.
		ArgumentCaptor<GoalConflictMessage> message = ArgumentCaptor.forClass(GoalConflictMessage.class);
		verify(mockMessageService, times(2)).sendMessage(message.capture(), eq(anonMessageDestination));
		assertThat("Expect right goals set to goal conflict messages",
				message.getAllValues().stream().map(m -> m.getGoal().getID()).collect(Collectors.toSet()),
				containsInAnyOrder(gamblingGoal.getID(), gamingGoal.getID()));
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
		DayActivity dayActivity = DayActivity.createInstance(userAnonID, gamblingGoal, LocalDate.now());
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
		// Verify that there is no conflict message sent.
		verify(mockMessageService, never()).sendMessage(any(), eq(anonMessageDestination));
	}
}