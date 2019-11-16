/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import nu.yona.server.Translator;
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
import nu.yona.server.sms.SmsService;
import nu.yona.server.sms.SmsTemplate;
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { MessageServiceTestConfiguration.class })
public class MessageServiceTest extends BaseSpringIntegrationTest
{
	private static final String FIREBASE_REGISTRATION_TOKEN = "Firebase-12345";

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

	@MockBean
	private SmsService mockSmsService;

	@Autowired
	private MessageService service;

	private static final String PASSWORD = "password";

	private UUID userAnonId;
	private UserAnonymized userAnonEntity;
	private DeviceAnonymized deviceAnonEntity;

	@BeforeEach
	public void setUp()
	{
		when(mockUserService.generatePassword()).thenReturn("topSecret");

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<>();
		deviceAnonEntity = DeviceAnonymized.createInstance(0, OperatingSystem.ANDROID, "Unknown", 5,
				Optional.of(FIREBASE_REGISTRATION_TOKEN), Translator.EN_US_LOCALE);
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

		verify(mockFirebaseService, times(1)).sendMessage(FIREBASE_REGISTRATION_TOKEN, message);
	}

	@Test
	public void sendDirectMessage_default_sendsSms()
	{
		Message message = Mockito.mock(Message.class);
		MessageDestination namedMessageDestination = Mockito.mock(MessageDestination.class);
		String mobileNumber = "+316123456789";
		User user = Mockito.mock(User.class);
		when(user.getNamedMessageDestination()).thenReturn(namedMessageDestination);
		when(user.getMobileNumber()).thenReturn(mobileNumber);

		service.sendDirectMessage(message, user);

		verify(mockSmsService, times(1)).send(mobileNumber, SmsTemplate.DIRECT_MESSAGE_NOTIFICATION, Collections.emptyMap());
	}

	@Test
	public void sendDirectMessage_userCreatedOnBuddyRequest_doesNotSendSms()
	{
		Message message = Mockito.mock(Message.class);
		MessageDestination namedMessageDestination = Mockito.mock(MessageDestination.class);
		User user = Mockito.mock(User.class);
		when(user.getNamedMessageDestination()).thenReturn(namedMessageDestination);
		when(user.isCreatedOnBuddyRequest()).thenReturn(true);

		service.sendDirectMessage(message, user);

		verify(mockSmsService, never()).send(any(), any(), any());
	}

	@Test
	public void prepareMessageCollection_transferringDirectMessagesToAnonymousDestination_sendsFirebaseMessage()
	{
		Message message = Mockito.mock(Message.class);
		when(message.duplicate()).thenReturn(message);

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

		verify(mockFirebaseService, times(1)).sendMessage(FIREBASE_REGISTRATION_TOKEN, message);
	}
}
