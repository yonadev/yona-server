/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.BuddyDevice;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.device.service.DeviceChange;
import nu.yona.server.email.EmailService;
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.MessageSourceRepositoryMock;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageRepository;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.sms.SmsService;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;
import nu.yona.server.subscriptions.entities.BuddyDeviceChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.InCryptoSession;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.LockPool;
import nu.yona.server.util.TransactionHelper;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.messaging.service", "nu.yona.server.properties" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.BuddyDeviceChangeMessageDto.Manager", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.BuddyService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.User.*Service", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.messaging.service.SenderInfo.Factory", type = FilterType.REGEX) }, excludeFilters = {
						@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserPhotoService", type = FilterType.REGEX) })
class BuddyDeviceChangeMessageDtoTestConfiguration extends UserRepositoriesConfiguration
{
	static final String PASSWORD = "password";

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

@ExtendWith(SpringExtension.class)
@InCryptoSession(BuddyDeviceChangeMessageDtoTestConfiguration.PASSWORD)
@ContextConfiguration(classes = { BuddyDeviceChangeMessageDtoTestConfiguration.class })
public class BuddyDeviceChangeMessageDtoTest extends BaseSpringIntegrationTest
{
	private static final String MESSAGE_TEXT = "Not relevant to test";
	private User richard;
	private User bob;

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

	@MockBean
	private LockPool<UUID> mockUserSynchronizer;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		try (CryptoSession cryptoSession = CryptoSession.start(BuddyDeviceChangeMessageDtoTestConfiguration.PASSWORD))
		{
			richard = JUnitUtil.createRichard();
			bob = JUnitUtil.createBob();
			JUnitUtil.makeBuddies(richard, bob);
		}
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		repositoriesMap.put(BuddyAnonymized.class, buddyAnonymizedRepository);
		repositoriesMap.put(Message.class, mockMessageRepository);
		repositoriesMap.put(MessageSource.class, new MessageSourceRepositoryMock());
		return repositoriesMap;
	}

	@Test
	public void managerHandleAction_deviceAdded_deviceExistsForBuddy()
	{
		// Add device
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		UserDevice device = addDevice(richard, deviceName, operatingSystem);

		// Verify device is present for Richard but not known to Bob
		assertThat(richard.getDevices(), containsInAnyOrder(device));
		Buddy buddy = bob.getBuddies().iterator().next();
		assertThat(buddy.getDevices().size(), equalTo(0));

		// Create the message
		BuddyInfoParameters buddyInfoParameters = BuddyInfoParameters.createInstance(richard);
		BuddyDeviceChangeMessage messageEntity = BuddyDeviceChangeMessage.createInstance(buddyInfoParameters, MESSAGE_TEXT,
				DeviceChange.ADD, device.getDeviceAnonymizedId(), Optional.empty(), Optional.of(deviceName));

		// Process the message
		manager.handleAction(bob, messageEntity, "process", null);

		// Assert success
		assertThat("Message is not processed", messageEntity.isProcessed());
		assertThat(buddy.getDevices().size(), equalTo(1));
		BuddyDevice buddyDevice = buddy.getDevices().iterator().next();
		assertThat(buddyDevice.getDeviceAnonymizedId(), equalTo(device.getDeviceAnonymizedId()));
		assertThat(buddyDevice.getName(), equalTo(deviceName));
	}

	@Test
	public void managerHandleAction_deviceRenamed_deviceIsRenamedForBuddy()
	{
		// Add device to Richard and Bob
		String orgDeviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		UserDevice device = addDevice(richard, orgDeviceName, operatingSystem);

		Buddy buddy = bob.getBuddies().iterator().next();
		BuddyDevice buddyDevice = addDeviceToBuddy(buddy, device);

		// Verify device is present for Richard and known to Bob
		assertThat(richard.getDevices(), containsInAnyOrder(device));
		assertThat(buddy.getDevices(), containsInAnyOrder(buddyDevice));

		// Create the message
		BuddyInfoParameters buddyInfoParameters = BuddyInfoParameters.createInstance(richard);
		String newDeviceName = "Renamed";
		BuddyDeviceChangeMessage messageEntity = BuddyDeviceChangeMessage.createInstance(buddyInfoParameters, MESSAGE_TEXT,
				DeviceChange.RENAME, device.getDeviceAnonymizedId(), Optional.of(orgDeviceName), Optional.of(newDeviceName));

		// Process the message
		manager.handleAction(bob, messageEntity, "process", null);

		// Assert success
		assertThat("Message is not processed", messageEntity.isProcessed());
		assertThat(buddy.getDevices(), containsInAnyOrder(buddyDevice));
		BuddyDevice updatedBuddyDevice = buddy.getDevices().iterator().next();
		assertThat(updatedBuddyDevice.getDeviceAnonymizedId(), equalTo(device.getDeviceAnonymizedId()));
		assertThat(updatedBuddyDevice.getName(), equalTo(newDeviceName));
	}

	@Test
	public void managerHandleAction_oneOfTwoDevicesDeleted_deviceIsDeletedForBuddy()
	{
		// Add devices to Richard and Bob
		String deviceName1 = "Device1";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		UserDevice device1 = addDevice(richard, deviceName1, operatingSystem1);

		Buddy buddy = bob.getBuddies().iterator().next();
		BuddyDevice buddyDevice1 = addDeviceToBuddy(buddy, device1);

		String deviceName2 = "Device2";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDevice device2 = addDevice(richard, deviceName2, operatingSystem2);

		BuddyDevice buddyDevice2 = addDeviceToBuddy(buddy, device2);

		// Verify devices are present for Richard and known to Bob
		assertThat(richard.getDevices(), containsInAnyOrder(device1, device2));
		assertThat(buddy.getDevices(), containsInAnyOrder(buddyDevice1, buddyDevice2));

		// Create the message
		BuddyInfoParameters buddyInfoParameters = BuddyInfoParameters.createInstance(richard);
		BuddyDeviceChangeMessage messageEntity = BuddyDeviceChangeMessage.createInstance(buddyInfoParameters, MESSAGE_TEXT,
				DeviceChange.DELETE, device2.getDeviceAnonymizedId(), Optional.of(deviceName2), Optional.empty());

		// Process the message
		manager.handleAction(bob, messageEntity, "process", null);

		// Assert success
		assertThat("Message is not processed", messageEntity.isProcessed());
		assertThat(buddy.getDevices(), containsInAnyOrder(buddyDevice1));
		BuddyDevice remainingBuddyDevice = buddy.getDevices().iterator().next();
		assertThat(remainingBuddyDevice.getDeviceAnonymizedId(), equalTo(device1.getDeviceAnonymizedId()));
		assertThat(remainingBuddyDevice.getName(), equalTo(deviceName1));
	}

	@Test
	public void managerHandleAction_deletedTheOneAndOnlyDevice_deviceIsDeletedForBuddy()
	{
		// Add devices to Richard and Bob
		String deviceName1 = "Device1";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		UserDevice device1 = addDevice(richard, deviceName1, operatingSystem1);

		Buddy buddy = bob.getBuddies().iterator().next();
		BuddyDevice buddyDevice1 = addDeviceToBuddy(buddy, device1);

		// Verify device is present for Richard and known to Bob
		assertThat(richard.getDevices(), containsInAnyOrder(device1));
		assertThat(buddy.getDevices(), containsInAnyOrder(buddyDevice1));

		// Create the message
		BuddyInfoParameters buddyInfoParameters = BuddyInfoParameters.createInstance(richard);
		BuddyDeviceChangeMessage messageEntity = BuddyDeviceChangeMessage.createInstance(buddyInfoParameters, MESSAGE_TEXT,
				DeviceChange.DELETE, device1.getDeviceAnonymizedId(), Optional.of(deviceName1), Optional.empty());

		// Process the message
		manager.handleAction(bob, messageEntity, "process", null);

		// Assert success
		assertThat("Message is not processed", messageEntity.isProcessed());
		assertThat(buddy.getDevices().size(), equalTo(0));
	}

	private BuddyDevice addDeviceToBuddy(Buddy buddy, UserDevice device)
	{
		BuddyDevice buddyDevice = BuddyDevice.createInstance(device.getName(), device.getDeviceAnonymizedId());
		buddy.addDevice(buddyDevice);
		return buddyDevice;
	}

	private UserDevice addDevice(User user, String deviceName, OperatingSystem operatingSystem)
	{
		DeviceAnonymized deviceAnonymized = DeviceAnonymized.createInstance(0, operatingSystem, "Unknown", 0, Optional.empty(),
				Translator.EN_US_LOCALE);
		deviceAnonymizedRepository.save(deviceAnonymized);
		UserDevice device = UserDevice.createInstance(user, deviceName, deviceAnonymized.getId(), "topSecret");
		user.addDevice(device);

		return device;
	}
}
