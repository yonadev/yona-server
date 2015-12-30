package nu.yona.server.analysis.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
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
import org.mockito.runners.MockitoJUnitRunner;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.Message;
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
	private GoalService mockGoalService;
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

	@Before
	public void setUp()
	{
		Goal gamblingGoal = Goal.createInstance("gambling", new HashSet<String>(Arrays.asList("poker", "lotto")));
		Goal newsGoal = Goal.createInstance("news", new HashSet<String>(Arrays.asList("refdag", "bbc")));

		goalMap.put(gamblingGoal.getName(), gamblingGoal);
		goalMap.put(newsGoal.getName(), newsGoal);

		when(mockYonaProperties.getAnalysisService()).thenReturn(new AnalysisServiceProperties());

		when(mockGoalService.getAllGoalEntities()).thenReturn(new HashSet<Goal>(goalMap.values()));
		when(mockGoalService.getAllGoals()).thenReturn(new HashSet<GoalDTO>(
				goalMap.values().stream().map(goal -> GoalDTO.createInstance(goal)).collect(Collectors.toSet())));
	}

	@Test
	public void getRelevantCategories()
	{
		assertEquals(new HashSet<String>(Arrays.asList("poker", "lotto", "refdag", "bbc")), service.getRelevantCategories());
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a matching category.
	 */
	@Test
	public void messageCreatedOnMatch()
	{
		// Set up UserAnonymized instance.
		MessageDestinationDTO anonMessageDestination = new MessageDestinationDTO(UUID.randomUUID());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(goalMap.get("gambling")));
		UserAnonymizedDTO userAnon = new UserAnonymizedDTO(goals, anonMessageDestination, Collections.emptySet());
		UUID userAnonID = UUID.randomUUID();

		// Stub the UserAnonymizedRepository to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonID)).thenReturn(userAnon);

		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto"));
		service.analyze(new PotentialConflictDTO(userAnonID, conflictCategories, "http://localhost/test"));

		// Verify that there is a new conflict message and that the message destination was saved to the repository.
		ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
		verify(mockMessageService).sendMessage(message.capture(), eq(anonMessageDestination));
		assertEquals(userAnonID, message.getValue().getRelatedUserAnonymizedID());
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnNoMatch()
	{
		// Set up UserAnonymized instance.
		MessageDestinationDTO anonMessageDestination = new MessageDestinationDTO(UUID.randomUUID());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(goalMap.get("news")));
		UserAnonymizedDTO userAnon = new UserAnonymizedDTO(goals, anonMessageDestination, Collections.emptySet());
		UUID userAnonID = UUID.randomUUID();

		// Stub the UserAnonymizedRepository to return our user.
		when(mockUserAnonymizedService.getUserAnonymized(userAnonID)).thenReturn(userAnon);

		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto"));
		service.analyze(new PotentialConflictDTO(userAnonID, conflictCategories, "http://localhost/test"));

		// Verify that there is a new conflict message and that the message destination was not saved to the repository.
		verify(mockMessageService, never()).sendMessage(null, anonMessageDestination);
	}
}