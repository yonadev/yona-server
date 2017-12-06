/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.repository.Repository;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.UserAnonymizedRepositoryMock;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;

public class AddFirstDeviceTest
{
	private static final String PASSWORD = "password";
	private User john;

	private UserAnonymizedRepository userAnonymizedRepository = new UserAnonymizedRepositoryMock();
	private DeviceAnonymizedRepository deviceAnonymizedRepository = new DeviceAnonymizedRepositoryMock();

	private MigrationStep migrationStep = new AddFirstDevice();

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
		userAnonymizedRepository.deleteAll();
		deviceAnonymizedRepository.deleteAll();
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(UserAnonymized.class, userAnonymizedRepository);
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);

		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			john = JUnitUtil.createUserEntity();
		}

	}

	@Test
	public void upgrade_addDeviceAnonymizedToUserAnonymized_linkEstablished()
	{
		// Add device
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		DeviceAnonymized deviceAnonymized = new DeviceAnonymized(UUID.randomUUID(), 0, operatingSystem);
		deviceAnonymizedRepository.save(deviceAnonymized);
		UserDevice device = new UserDevice(UUID.randomUUID(), deviceName, deviceAnonymized.getId());
		john.addDevice(device);

		// Verify device is present
		Set<UserDevice> devices = john.getDevices();
		assertThat(devices.size(), equalTo(1));

		// Remove all devices from user anonymized
		new ArrayList<>(john.getAnonymized().getDevicesAnonymized()).forEach(d -> john.getAnonymized().removeDeviceAnonymized(d));
		assertThat(john.getAnonymized().getDevicesAnonymized().size(), equalTo(0));

		// Verify they're gone
		devices = john.getDevices();
		UserDevice deviceToBeMigrated = devices.iterator().next();
		assertThat(deviceToBeMigrated.getDeviceAnonymized().getUserAnonymized(), nullValue());
		assertThat(john.getAnonymized().getDevicesAnonymized().size(), equalTo(0));

		// Migrate and assert success
		migrationStep.upgrade(john);
		devices = john.getDevices();
		assertThat(devices.size(), equalTo(1));
		UserDevice deviceAsMigrated = devices.iterator().next();
		assertThat(deviceAsMigrated.getDeviceAnonymized().getUserAnonymized(), notNullValue());
		assertThat(john.getAnonymized().getDevicesAnonymized().size(), equalTo(1));
		assertThat(john.getAnonymized().getDevicesAnonymized().contains(deviceAnonymized), equalTo(true));
	}
}