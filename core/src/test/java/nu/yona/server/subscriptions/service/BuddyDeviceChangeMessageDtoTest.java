/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.BuddyDevice;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.service.DeviceChange;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.email.EmailService;
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.UserAnonymizedRepositoryMock;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoryMock;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageRepository;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;
import nu.yona.server.subscriptions.entities.BuddyDeviceChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.TransactionHelper;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.messaging.service", "nu.yona.server.properties" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.BuddyDeviceChangeMessageDto.Manager", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.BuddyService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.messaging.service.SenderInfo.Factory", type = FilterType.REGEX) })
class BuddyDeviceChangeMessageDtoTestConfiguration
{
	@Bean
	UserRepository getMockUserRepository()
	{
		return new UserRepositoryMock();
	}

	@Bean
	UserAnonymizedRepository getMockUserAnonymizedRepository()
	{
		return new UserAnonymizedRepositoryMock();
	}

	@Bean
	UserDeviceRepository getMockUserDeviceRepository()
	{
		return new UserDeviceRepositoryMock();
	}

	@Bean
	DeviceAnonymizedRepository getDeviceAnonymizedRepository()
	{
		return new DeviceAnonymizedRepositoryMock();
	}

	@Bean
	BuddyAnonymizedRepository getBuddyAnonymizedRepository()
	{
		return new BuddyAnonymizedRepositoryMock();
	}
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { BuddyDeviceChangeMessageDtoTestConfiguration.class })
public class BuddyDeviceChangeMessageDtoTest
{
	private static final String PASSWORD = "password";
	private User richard;
	private User bob;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserAnonymizedRepository userAnonymizedRepository;

	@Autowired
	private UserDeviceRepository userDeviceRepository;

	@Autowired
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@Autowired
	private BuddyAnonymizedRepository buddyAnonymizedRepository;

	@Mock
	private MessageRepository mockMessageRepository;

	@Autowired
	private BuddyDeviceChangeMessageDto.Manager manager;

	@MockBean
	private UserAnonymizedService mockUserAnonymizedService;

	@MockBean
	private BuddyConnectResponseMessageDto.Manager mockBuddyConnectResponseMessageDto_Manager;

	@MockBean
	private TransactionHelper mockTransactionHelper;

	@MockBean
	private EmailService mockEmailService;

	@MockBean
	private SmsService mockSmsService;

	@MockBean
	private Translator mockTranslator;

	@MockBean
	private TheDtoManager mockTheDtoManager;

	@MockBean
	private MessageService mockMessageService;

	@Captor
	private ArgumentCaptor<Supplier<Message>> messageSupplierCaptor;

	@Rule
	public MethodRule cryptoSession = new CryptoSessionRule(PASSWORD);

	@BeforeClass
	public static void setUpForAll()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@Before
	public void setUpPerTest() throws Exception
	{
		MockitoAnnotations.initMocks(this);

		userRepository.deleteAll();
		userAnonymizedRepository.deleteAll();
		userDeviceRepository.deleteAll();
		deviceAnonymizedRepository.deleteAll();
		buddyAnonymizedRepository.deleteAll();
		JUnitUtil.setUpRepositoryMock(mockMessageRepository);

		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(UserAnonymized.class, userAnonymizedRepository);
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		repositoriesMap.put(BuddyAnonymized.class, buddyAnonymizedRepository);
		repositoriesMap.put(Message.class, mockMessageRepository);
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);

		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			richard = JUnitUtil.createRichard();
			bob = JUnitUtil.createBob();
			JUnitUtil.makeBuddies(richard, bob);
			userRepository.save(richard);
			userRepository.save(bob);
		}
	}

	@Test
	public void managerHandleAction_deviceAdded_deviceExistsForBuddy()
	{
		// Add device
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		DeviceAnonymized deviceAnonymized = new DeviceAnonymized(UUID.randomUUID(), 0, operatingSystem);
		deviceAnonymizedRepository.save(deviceAnonymized);
		UserDevice device = UserDevice.createInstance(deviceName, deviceAnonymized.getId());
		richard.addDevice(device);

		// Verify device is present for Richard but not known to Bob
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.size(), equalTo(1));
		Buddy buddy = bob.getBuddies().iterator().next();
		assertThat(buddy.getDevices().size(), equalTo(0));

		// Create the message
		BuddyInfoParameters buddyInfoParameters = BuddyInfoParameters.createInstance(richard);
		BuddyDeviceChangeMessage messageEntity = BuddyDeviceChangeMessage.createInstance(buddyInfoParameters, "My new device",
				DeviceChange.ADD, deviceAnonymized.getId(), Optional.empty(), Optional.of(deviceName));

		// Process the message
		manager.handleAction(UserDto.createInstance(bob), messageEntity, "process", null);

		// Assert success
		assertTrue(messageEntity.isProcessed());
		assertThat(buddy.getDevices().size(), equalTo(1));
		BuddyDevice buddyDevice = buddy.getDevices().iterator().next();
		assertThat(buddyDevice.getDeviceAnonymizedId(), equalTo(deviceAnonymized.getId()));
		assertThat(buddyDevice.getName(), equalTo(deviceName));
	}

	// TODO: Add tests for delete and update
}
