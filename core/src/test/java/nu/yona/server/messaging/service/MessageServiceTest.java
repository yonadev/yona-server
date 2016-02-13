package nu.yona.server.messaging.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.service.GoalConflictMessageDTO;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageDestinationRepository;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserPrivateDTO;
import nu.yona.server.subscriptions.service.UserService;

@RunWith(MockitoJUnitRunner.class)
public class MessageServiceTest
{
	@Mock
	private UserService mockUserService;
	@Mock
	private TheDTOManager mockTheDTOManager;
	@Mock
	private MessageDestinationRepository mockMessageDestinationRepository;
	@Mock
	private MessageSourceRepository mockMessageSourceRepository;
	@InjectMocks
	private MessageService service = new MessageService();

	private UserDTO user;

	@Mock
	private MessageSource mockNamedMessageSource;
	private UUID namedMessageSourceID = UUID.randomUUID();
	@Mock
	private MessageDestination mockNamedMessageDestination;
	private UUID namedMessageDestinationID = UUID.randomUUID();
	@Mock
	private MessageSource mockAnonymousMessageSource;
	private UUID anonymousMessageSourceID = UUID.randomUUID();
	@Mock
	private MessageDestination mockAnonymousMessageDestination;
	private UUID anonymousMessageDestinationID = UUID.randomUUID();

	@Before
	public void setUp()
	{
		when(mockAnonymousMessageSource.getID()).thenReturn(anonymousMessageSourceID);
		when(mockAnonymousMessageDestination.getID()).thenReturn(anonymousMessageDestinationID);
		when(mockNamedMessageSource.getID()).thenReturn(namedMessageSourceID);
		when(mockNamedMessageDestination.getID()).thenReturn(namedMessageDestinationID);
		when(mockAnonymousMessageSource.getDestination()).thenReturn(mockAnonymousMessageDestination);
		when(mockNamedMessageSource.getDestination()).thenReturn(mockNamedMessageDestination);

		// Set up User instance.
		user = new UserDTO(UUID.randomUUID(), "John", "Smith", null, "+31612345678", true,
				new UserPrivateDTO("Johnny", namedMessageSourceID, namedMessageDestinationID, anonymousMessageSourceID,
						anonymousMessageDestinationID, new HashSet<String>(Arrays.asList("iPhone 5")), new HashSet<GoalDTO>(),
						new HashSet<UUID>(), UUID.randomUUID(), null));

		// Stub the UserAnonymizedRepository to return our user.
		when(mockUserService.getPrivateValidatedUser(user.getID())).thenReturn(user);

		when(mockMessageSourceRepository.findOne(user.getPrivateData().getAnonymousMessageSourceID()))
				.thenReturn(mockAnonymousMessageSource);
		when(mockMessageSourceRepository.findOne(user.getPrivateData().getNamedMessageSourceID()))
				.thenReturn(mockNamedMessageSource);

		when(mockMessageDestinationRepository.findOne(user.getPrivateData().getAnonymousMessageDestinationID()))
				.thenReturn(mockAnonymousMessageDestination);
		when(mockMessageDestinationRepository.findOne(user.getPrivateData().getNamedMessageDestinationID()))
				.thenReturn(mockNamedMessageDestination);
		when(mockMessageDestinationRepository.save(any(MessageDestination.class))).thenAnswer(new Answer<MessageDestination>() {
			@Override
			public MessageDestination answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (MessageDestination) args[0];
			}
		});
	}

	@Test
	public void getAnonymousMessage()
	{
		GoalConflictMessage message = GoalConflictMessage.createInstance(user.getPrivateData().getUserAnonymizedID(),
				Activity.createInstance(user.getPrivateData().getUserAnonymizedID(),
						BudgetGoal.createInstance(ActivityCategory.createInstance("gambling", false,
								new HashSet<String>(Arrays.asList("poker", "lotto")), new HashSet<String>()), 30),
						new Date()),
				"http://poker.nu");

		when(mockAnonymousMessageSource.getMessage(message.getID())).thenReturn(message);
		when(mockTheDTOManager.createInstance(user, message))
				.thenReturn(GoalConflictMessageDTO.createInstance(message, user.getPrivateData().getNickname()));

		MessageDTO result = service.getAnonymousMessage(user.getID(), message.getID());
		verify(mockAnonymousMessageSource).getMessage(message.getID());
		assertThat(result.getID(), equalTo(message.getID()));
		assertThat(result.getCreationTime(), equalTo(message.getCreationTime()));
		assertThat(result.getClass(), equalTo(GoalConflictMessageDTO.class));
	}
}