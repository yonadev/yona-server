package nu.yona.server.analysis.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import nu.yona.server.analysis.service.AnalysisEngineService;
import nu.yona.server.analysis.service.PotentialConflictDTO;
import nu.yona.server.crypto.PublicKeyUtil;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageDestinationRepository;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;

@RunWith(MockitoJUnitRunner.class)
public class AnalysisServiceTests
{
	private Map<String, Goal> goalMap = new HashMap<String, Goal>();
	private KeyPair encryptionKeyPair = PublicKeyUtil.generateKeyPair();

	@Mock
	private GoalService mockGoalService;
	@Mock
	private UserAnonymizedRepository userAnonymizedRepository;
	@Mock
	private MessageDestinationRepository messageDestinationRepository;
	@InjectMocks
	private AnalysisEngineService service = new AnalysisEngineService();

	@Before
	public void setUp()
	{
		Goal gamblingGoal = Goal.createInstance("gambling", new HashSet<String>(Arrays.asList("poker", "lotto")));
		Goal newsGoal = Goal.createInstance("news", new HashSet<String>(Arrays.asList("refdag", "bbc")));

		goalMap.put(gamblingGoal.getName(), gamblingGoal);
		goalMap.put(newsGoal.getName(), newsGoal);

		when(mockGoalService.getAllGoalEntities()).thenReturn(new HashSet<Goal>(goalMap.values()));
	}

	/**
	 * Tests that a conflict message is created when analysis service is called with a matching category.
	 */
	@Test
	public void messageCreatedOnMatch()
	{
		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestination = MessageDestination.createInstance(encryptionKeyPair.getPublic());
		Set<Goal> goals = new HashSet<Goal>(Arrays.asList(goalMap.get("gambling")));
		UserAnonymized userAnon = UserAnonymized.createInstance(anonMessageDestination, goals);

		// Stub the UserAnonymizedRepository to return our user.
		when(userAnonymizedRepository.findOne(userAnon.getID())).thenReturn(userAnon);

		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto"));
		service.analyze(new PotentialConflictDTO(userAnon.getID(), conflictCategories, "http://localhost/test"));

		// Verify that there is a new conflict message and that the message destination was saved to the repository.
		assertEquals(1, anonMessageDestination.getAllMessages().size());
		assertEquals(userAnon.getID(), anonMessageDestination.getAllMessages().get(0).getRelatedLoginID());
		verify(messageDestinationRepository).save(anonMessageDestination);
	}

	/**
	 * Tests that no conflict messages are created when analysis service is called with non-matching category.
	 */
	@Test
	public void noMessagesCreatedOnNoMatch()
	{
		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestination = MessageDestination.createInstance(encryptionKeyPair.getPublic());
		Set<Goal> userGoals = new HashSet<Goal>(Arrays.asList(goalMap.get("news")));
		UserAnonymized userAnon = UserAnonymized.createInstance(anonMessageDestination, userGoals);

		// Stub the UserAnonymizedRepository to return our user.
		when(userAnonymizedRepository.findOne(userAnon.getID())).thenReturn(userAnon);

		// Execute the analysis engine service.
		Set<String> conflictCategories = new HashSet<String>(Arrays.asList("lotto"));
		service.analyze(new PotentialConflictDTO(userAnon.getID(), conflictCategories, "http://localhost/test"));

		// Verify that there is a new conflict message and that the message destination was saved to the repository.
		assertEquals(0, anonMessageDestination.getAllMessages().size());
		verify(messageDestinationRepository, never()).save(anonMessageDestination);
	}
}