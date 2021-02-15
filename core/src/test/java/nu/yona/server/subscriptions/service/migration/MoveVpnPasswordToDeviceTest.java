/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.MessageSourceRepositoryMock;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.InCryptoSession;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.device.service", "nu.yona.server.properties", "nu.yona.server" }, includeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.migration.MoveVpnPasswordToDevice", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.device.service.DeviceService", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.Translator", type = FilterType.REGEX) })
class MoveVpnPasswordToDeviceTestConfiguration extends UserRepositoriesConfiguration
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
@InCryptoSession(MoveVpnPasswordToDeviceTestConfiguration.PASSWORD)
@ContextConfiguration(classes = { MoveVpnPasswordToDeviceTestConfiguration.class })
public class MoveVpnPasswordToDeviceTest extends BaseSpringIntegrationTest
{
	private static final Field vpnPasswordField = JUnitUtil.getAccessibleField(UserPrivate.class, "vpnPassword");
	private static final Field userPrivateField = JUnitUtil.getAccessibleField(User.class, "userPrivate");
	private User richard;

	@Autowired
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@Autowired
	private MigrationStep migrationStep;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		try (CryptoSession cryptoSession = CryptoSession.start(MoveVpnPasswordToDeviceTestConfiguration.PASSWORD))
		{
			richard = JUnitUtil.createRichard();
		}
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		repositoriesMap.put(MessageSource.class, new MessageSourceRepositoryMock());
		return repositoriesMap;
	}

	@Test
	public void upgrade_movePassword_passwordMoved() throws IllegalArgumentException, IllegalAccessException
	{
		String vpnPassword = "upgrade_movePassword_passwordMoved";
		vpnPasswordField.set(userPrivateField.get(richard), vpnPassword);
		UserDevice device = createDevice();
		richard.addDevice(device);

		migrationStep.upgrade(richard);

		assertThat(device.getVpnPassword(), equalTo(vpnPassword));
		assertThat(richard.getAndClearVpnPassword(), equalTo(Optional.empty()));
	}

	@Test
	public void upgrade_movePasswordTwice_idemPotent() throws IllegalArgumentException, IllegalAccessException
	{
		String vpnPassword = "upgrade_movePasswordTwice_idemPotent";
		vpnPasswordField.set(userPrivateField.get(richard), vpnPassword);
		UserDevice device = createDevice();
		richard.addDevice(device);

		migrationStep.upgrade(richard);
		migrationStep.upgrade(richard);

		assertThat(device.getVpnPassword(), equalTo(vpnPassword));
		assertThat(richard.getAndClearVpnPassword(), equalTo(Optional.empty()));
	}

	private UserDevice createDevice()
	{
		DeviceAnonymized deviceAnonymized = DeviceAnonymized
				.createInstance(0, OperatingSystem.ANDROID, "1.0.0", 2, Optional.empty(), Translator.EN_US_LOCALE);
		deviceAnonymizedRepository.save(deviceAnonymized);
		return UserDevice.createInstance(richard, "Testing", deviceAnonymized.getId(), "toBeOverwritten");
	}
}
