/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import nu.yona.server.CoreConfiguration;
import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.device.service.DeviceChange;
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.GoalRepositoryMock;
import nu.yona.server.entities.MessageSourceRepositoryMock;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalRepository;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;
import nu.yona.server.subscriptions.entities.BuddyDeviceChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.service.LDAPUserService;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.InCryptoSession;
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
	static final String PASSWORD = "password";

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
	GoalRepository getMockGoalRepository()
	{
		return new GoalRepositoryMock();
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
@InCryptoSession(AddFirstDeviceIntegrationTestConfiguration.PASSWORD)
@ContextConfiguration(classes = { AddFirstDeviceIntegrationTestConfiguration.class })
public class AddFirstDeviceTest extends BaseSpringIntegrationTest
{
	private static final String SOME_APP_VERSION = "9.9.9";
	private static final int SUPPORTED_APP_VERSION_CODE = 999;
	private User richard;
	private User bob;

	@Autowired
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@Autowired
	private BuddyAnonymizedRepository buddyAnonymizedRepository;

	@Autowired
	private UserAnonymizedRepository userAnonymizedRepository;

	@Autowired
	private MigrationStep migrationStep;

	@MockBean
	private MessageService mockMessageService;

	@MockBean
	private UserService mockUserService;

	@MockBean
	private UserAnonymizedService mockUserAnonymizedService;

	@MockBean
	private LDAPUserService mockLdapUserService;

	@Autowired
	private GoalRepository goalRepository;

	@Captor
	private ArgumentCaptor<Supplier<Message>> messageSupplierCaptor;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		when(mockUserService.generatePassword()).thenReturn("topSecret");
		try (CryptoSession cryptoSession = CryptoSession.start(AddFirstDeviceIntegrationTestConfiguration.PASSWORD))
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
		repositoriesMap.put(Goal.class, goalRepository);
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		repositoriesMap.put(BuddyAnonymized.class, buddyAnonymizedRepository);
		repositoriesMap.put(MessageSource.class, new MessageSourceRepositoryMock());
		return repositoriesMap;
	}

	@Test
	public void upgrade_addDeviceAnonymizedToUserAnonymized_linkEstablished()
	{
		// Add device
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		DeviceAnonymized deviceAnonymized = DeviceAnonymized.createInstance(0, operatingSystem, SOME_APP_VERSION,
				SUPPORTED_APP_VERSION_CODE, Optional.empty(), Translator.EN_US_LOCALE);
		deviceAnonymizedRepository.save(deviceAnonymized);
		UserDevice device = UserDevice.createInstance(richard, deviceName, deviceAnonymized.getId(), "topSecret");
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
		verify(mockLdapUserService, never()).createVpnAccount(any(), any()); // User has legacy VPN account

	}

	@Test
	public void upgrade_addFirstDevice_userHasDevice()
	{
		UserAnonymizedDto userAnonDto = UserAnonymizedDto.createInstance(richard.getAnonymized());
		when(mockUserAnonymizedService.getUserAnonymized(richard.getUserAnonymizedId())).thenReturn(userAnonDto);
		when(mockUserAnonymizedService.updateUserAnonymized(richard.getUserAnonymizedId()))
				.thenAnswer(new Answer<UserAnonymizedDto>() {
					@Override
					public UserAnonymizedDto answer(InvocationOnMock invocation)
					{
						Object[] args = invocation.getArguments();
						return UserAnonymizedDto.createInstance(userAnonymizedRepository.findById((UUID) args[0]).get());
					}
				});
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

		verify(mockMessageService, times(1)).broadcastMessageToBuddies(ArgumentMatchers.<UserAnonymizedDto> any(),
				messageSupplierCaptor.capture());

		Message message = messageSupplierCaptor.getValue().get();
		assertThat(message, instanceOf(BuddyDeviceChangeMessage.class));
		BuddyDeviceChangeMessage buddyDeviceChangeMessage = (BuddyDeviceChangeMessage) message;
		assertThat(buddyDeviceChangeMessage.getChange(), equalTo(DeviceChange.ADD));
		assertThat(buddyDeviceChangeMessage.getNewName().get(), equalTo("First device"));
		assertThat(buddyDeviceChangeMessage.getMessage(), containsString("added a device"));
	}
}
