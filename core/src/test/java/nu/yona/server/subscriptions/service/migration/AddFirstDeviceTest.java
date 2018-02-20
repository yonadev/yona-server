/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.CoreConfiguration;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.device.service.DeviceChange;
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;
import nu.yona.server.subscriptions.entities.BuddyDeviceChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.device.service", "nu.yona.server.properties", "nu.yona.server" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.migration.AddFirstDevice", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.device.service.DeviceService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.Translator", type = FilterType.REGEX) })
class AddFirstDeviceIntegrationTestConfiguration extends UserRepositoriesConfiguration
{
	@Bean(name = "messageSource")
	public ReloadableResourceBundleMessageSource messageSource()
	{
		return new CoreConfiguration().messageSource();
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
@ContextConfiguration(classes = { AddFirstDeviceIntegrationTestConfiguration.class })
public class AddFirstDeviceTest extends BaseSpringIntegrationTest
{
	private static final String SUPPORTED_APP_VERSION = "9.9.9";
	private static final String PASSWORD = "password";
	private User richard;
	private User bob;

	@Autowired
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@Autowired
	private BuddyAnonymizedRepository buddyAnonymizedRepository;

	@Autowired
	private MigrationStep migrationStep = new AddFirstDevice();

	@MockBean
	private MessageService mockMessageService;

	@Captor
	private ArgumentCaptor<Supplier<Message>> messageSupplierCaptor;

	@Rule
	public MethodRule cryptoSession = new CryptoSessionRule(PASSWORD);

	@Before
	public void setUpPerTest() throws Exception
	{
		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
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
		return repositoriesMap;
	}

	@Test
	public void upgrade_addDeviceAnonymizedToUserAnonymized_linkEstablished()
	{
		// Add device
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		DeviceAnonymized deviceAnonymized = DeviceAnonymized.createInstance(0, operatingSystem, SUPPORTED_APP_VERSION);
		deviceAnonymizedRepository.save(deviceAnonymized);
		UserDevice device = UserDevice.createInstance(deviceName, deviceAnonymized.getId());
		richard.addDevice(device);

		// Verify device is present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.size(), equalTo(1));

		// Remove all devices from user anonymized
		new ArrayList<>(richard.getAnonymized().getDevicesAnonymized())
				.forEach(d -> richard.getAnonymized().removeDeviceAnonymized(d));
		assertThat(richard.getAnonymized().getDevicesAnonymized().size(), equalTo(0));

		// Verify they're gone
		devices = richard.getDevices();
		UserDevice deviceToBeMigrated = devices.iterator().next();
		assertThat(deviceToBeMigrated.getDeviceAnonymized().getUserAnonymized(), nullValue());
		assertThat(richard.getAnonymized().getDevicesAnonymized().size(), equalTo(0));

		// Migrate and assert success
		migrationStep.upgrade(richard);
		devices = richard.getDevices();
		assertThat(devices.size(), equalTo(1));
		UserDevice deviceAsMigrated = devices.iterator().next();
		assertThat(deviceAsMigrated.getDeviceAnonymized().getUserAnonymized(), equalTo(richard.getAnonymized()));
		assertThat(richard.getAnonymized().getDevicesAnonymized().size(), equalTo(1));
		assertThat(richard.getAnonymized().getDevicesAnonymized().contains(deviceAnonymized), equalTo(true));
	}

	@Test
	public void upgrade_addFirstDevice_userHasDevice()
	{
		// Verify Richard doesn't have any devices
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.size(), equalTo(0));

		// Migrate and assert success
		migrationStep.upgrade(richard);
		devices = richard.getDevices();
		assertThat(devices.size(), equalTo(1));
		UserDevice device = devices.iterator().next();
		assertThat(device.getName(), equalTo("First device"));
		assertThat(device.getDeviceAnonymized().getUserAnonymized(), equalTo(richard.getAnonymized()));
		assertThat(device.getDeviceAnonymized().getOperatingSystem(), equalTo(OperatingSystem.UNKNOWN));
		assertThat(richard.getAnonymized().getDevicesAnonymized().size(), equalTo(1));

		verify(mockMessageService, times(1)).broadcastMessageToBuddies(Matchers.<UserAnonymizedDto> any(),
				messageSupplierCaptor.capture());

		Message message = messageSupplierCaptor.getValue().get();
		assertThat(message, instanceOf(BuddyDeviceChangeMessage.class));
		BuddyDeviceChangeMessage buddyDeviceChangeMessage = (BuddyDeviceChangeMessage) message;
		assertThat(buddyDeviceChangeMessage.getChange(), equalTo(DeviceChange.ADD));
		assertThat(buddyDeviceChangeMessage.getNewName().get(), equalTo("First device"));
		assertThat(buddyDeviceChangeMessage.getMessage(), containsString("added a device"));
	}
}