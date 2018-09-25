/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalRepository;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageDestinationRepository;
import nu.yona.server.messaging.entities.MessageRepository;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.test.util.BaseSpringIntegrationTest;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.messaging.service",
		"nu.yona.server.subscriptions.service", "nu.yona.server.properties", "nu.yona.server" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.messaging.service.MessageService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) })
class MessageServiceTestConfiguration extends UserRepositoriesConfiguration
{
	@Bean
	UserDeviceRepository getMockDeviceRepository()
	{
		return Mockito.spy(new UserDeviceRepositoryMock());
	}

	@Bean
	DeviceAnonymizedRepository getMockDeviceAnonymizedRepository()
	{
		return Mockito.spy(new DeviceAnonymizedRepositoryMock());
	}
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { MessageServiceTestConfiguration.class })
public class MessageServiceTest extends BaseSpringIntegrationTest
{
	@Autowired
	private UserDeviceRepository userDeviceRepository;

	@Autowired
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@MockBean
	private UserService mockUserService;

	@MockBean
	private UserAnonymizedService mockUserAnonymizedService;

	@MockBean
	private GoalRepository mockGoalRepository;

	@MockBean
	private MessageSourceRepository mockMessageSourceRepository;

	@MockBean
	private MessageDestinationRepository mockMessageDestinationRepository;

	@MockBean
	private MessageRepository mockMessageRepository;

	@MockBean
	private FirebaseService mockFirebaseService;

	@Autowired
	private MessageService service;

	private static final String PASSWORD = "password";

	private UUID userAnonId;
	private UserAnonymized userAnonEntity;
	private DeviceAnonymized deviceAnonEntity;

	@Before
	public void setUp()
	{
		when(mockUserService.generatePassword()).thenReturn("topSecret");

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<>();
		deviceAnonEntity = DeviceAnonymized.createInstance(0, OperatingSystem.ANDROID, "Unknown", 5,
				Optional.of("Firebase-12345"));
		deviceAnonymizedRepository.save(deviceAnonEntity);
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		userAnonEntity.addDeviceAnonymized(deviceAnonEntity);
		UserAnonymizedDto userAnon = UserAnonymizedDto.createInstance(userAnonEntity);
		userAnonId = userAnon.getId();

		when(mockUserAnonymizedService.getUserAnonymized(userAnonId))
				.thenReturn(UserAnonymizedDto.createInstance(userAnonEntity));

		when(mockMessageDestinationRepository.findById(anonMessageDestinationEntity.getId()))
				.thenReturn(Optional.of(anonMessageDestinationEntity));
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(MessageSource.class, mockMessageSourceRepository);
		repositoriesMap.put(MessageDestination.class, mockMessageDestinationRepository);
		repositoriesMap.put(Message.class, mockMessageRepository);
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		repositoriesMap.put(UserDevice.class, userDeviceRepository);
		repositoriesMap.put(Goal.class, mockGoalRepository);
		return repositoriesMap;
	}

	@Test
	public void sendMessage_anonymousDestination_sendsFirebaseMessage()
	{
		Message message = Mockito.mock(Message.class);

		service.sendMessage(message, UserAnonymizedDto.createInstance(userAnonEntity));

		verify(mockFirebaseService, times(1)).sendMessage("Firebase-12345", message);
	}

	@Test
	public void sendMessage_namedDestination_doesNotSendFirebaseMessage()
	{
		Message message = Mockito.mock(Message.class);

		MessageDestination namedMessageDestination = Mockito.mock(MessageDestination.class);

		User user = Mockito.mock(User.class);
		when(user.getNamedMessageDestination()).thenReturn(namedMessageDestination);

		service.sendNamedMessage(message, user);

		verify(mockFirebaseService, never()).sendMessage("Firebase-12345", message);
	}

	@Test
	public void prepareMessageCollection_transferringDirectMessagesToAnonymousDestination_sendsFirebaseMessage()
	{
		Message message = Mockito.mock(Message.class);

		User user = Mockito.mock(User.class);
		when(user.getUserAnonymizedId()).thenReturn(userAnonId);
		UUID namedMessageSourceId = UUID.randomUUID();
		UUID anonymousMessageSourceId = UUID.randomUUID();
		when(user.getNamedMessageSourceId()).thenReturn(namedMessageSourceId);
		when(user.getAnonymousMessageSourceId()).thenReturn(anonymousMessageSourceId);

		MessageSource anonymousMessageSource = Mockito.mock(MessageSource.class);
		MessageDestination anonymousMessageDestination = Mockito.mock(MessageDestination.class);
		when(anonymousMessageSource.getDestination()).thenReturn(anonymousMessageDestination);

		MessageSource namedMessageSource = Mockito.mock(MessageSource.class);
		MessageDestination namedMessageDestination = Mockito.mock(MessageDestination.class);
		when(namedMessageSource.getMessages(null)).thenReturn(new PageImpl<Message>(Arrays.asList(message)));
		when(namedMessageSource.getDestination()).thenReturn(namedMessageDestination);

		when(mockMessageSourceRepository.findById(namedMessageSourceId)).thenReturn(Optional.of(namedMessageSource));
		when(mockMessageSourceRepository.findById(anonymousMessageSourceId)).thenReturn(Optional.of(anonymousMessageSource));

		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			service.prepareMessageCollection(user);
		}

		verify(mockFirebaseService, times(1)).sendMessage("Firebase-12345", message);
	}
}
