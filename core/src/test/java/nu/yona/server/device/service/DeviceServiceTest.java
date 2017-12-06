/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import static com.spencerwi.hamcrestJDK8Time.matchers.IsBetween.between;
import static nu.yona.server.test.util.Matchers.hasMessageId;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
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
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.entities.MockJpaRepositoryEntityWithUuid;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.TimeUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.device.service",
		"nu.yona.server.properties" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.device.service.DeviceService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) })
class DeviceServiceTestConfiguration
{
	@Bean
	DeviceAnonymizedRepository getMockDeviceAnonymizedRepository()
	{
		return new DeviceAnonymizedRepositoryMock();
	}

	@Bean
	UserAnonymizedRepository getMockUserAnonymizedRepository()
	{
		return new UserAnonymizedRepositoryMock();
	}

	private static class DeviceAnonymizedRepositoryMock extends MockJpaRepositoryEntityWithUuid<DeviceAnonymized>
			implements DeviceAnonymizedRepository
	{
	}

	private static class UserAnonymizedRepositoryMock extends MockJpaRepositoryEntityWithUuid<UserAnonymized>
			implements UserAnonymizedRepository
	{

		@Override
		public int countByLastMonitoredActivityDateBetween(LocalDate startDate, LocalDate endDate)
		{
			throw new NotImplementedException();
		}

		@Override
		public int countByLastMonitoredActivityDateIsNull()
		{
			throw new NotImplementedException();
		}
	}
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { DeviceServiceTestConfiguration.class })
public class DeviceServiceTest
{
	@MockBean
	private UserDeviceRepository mockUserDeviceRepository;

	@Autowired
	private DeviceService service;

	@Autowired
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@Autowired
	private UserAnonymizedRepository userAnonymizedRepository;

	@MockBean
	private UserRepository mockUserRepository;

	private static final String PASSWORD = "password";
	private User john;

	@Rule
	public MethodRule cryptoSession = new CryptoSessionRule(PASSWORD);

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Before
	public void setUpForAll()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@Before
	public void setUpPerTest()
	{
		deviceAnonymizedRepository.deleteAll();
		userAnonymizedRepository.deleteAll();
		JUnitUtil.setUpRepositoryMock(mockUserDeviceRepository);
		JUnitUtil.setUpRepositoryMock(mockUserRepository);

		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(UserAnonymized.class, userAnonymizedRepository);
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);

		MessageSource anonymousMessageSource = MessageSource.createInstance();
		UserAnonymized johnAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(),
				Collections.emptySet());
		UserAnonymized.getRepository().save(johnAnonymized);

		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
			MessageSource namedMessageSource = MessageSource.createInstance();
			UserPrivate userPrivate = UserPrivate.createInstance("jd", "topSecret", johnAnonymized.getId(),
					anonymousMessageSource.getId(), namedMessageSource);
			john = new User(UUID.randomUUID(), initializationVector, "John", "Doe", "+31612345678", userPrivate,
					namedMessageSource.getDestination());
		}

		when(mockUserRepository.findOne(john.getId())).thenReturn(john);
	}

	@Test
	public void getDevice_tryGetNonExistingDevice_exception() throws Exception
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.not.found.id"));
		service.getDevice(UUID.randomUUID());
	}

	@Test
	public void addDeviceToUser_addFirstDevice_userHasOneDevice()
	{
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDeviceDto deviceDto = new UserDeviceDto(deviceName, operatingSystem);
		service.addDeviceToUser(john, deviceDto);

		verify(mockUserDeviceRepository, times(1)).save(any(UserDevice.class));
		assertThat(deviceAnonymizedRepository.count(), equalTo(1L));

		Set<UserDevice> devices = john.getDevices();
		assertThat(devices.size(), equalTo(1));

		UserDevice device = devices.iterator().next();
		assertDevice(device, startTime, deviceName, operatingSystem, 0);
	}

	@Test
	public void addDeviceToUser_addSecondDevice_userHasTwoDevices()
	{
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDeviceDto deviceDto1 = new UserDeviceDto(deviceName1, operatingSystem1);
		service.addDeviceToUser(john, deviceDto1);

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDeviceDto deviceDto2 = new UserDeviceDto(deviceName2, operatingSystem2);
		service.addDeviceToUser(john, deviceDto2);

		verify(mockUserDeviceRepository, times(2)).save(any(UserDevice.class));
		assertThat(deviceAnonymizedRepository.count(), equalTo(2L));

		Set<UserDevice> devices = john.getDevices();
		assertThat(devices.size(), equalTo(2));

		Optional<UserDevice> device1Optional = devices.stream().filter(d -> d.getName().equals(deviceName1)).findAny();
		assertThat(device1Optional.isPresent(), equalTo(true));
		UserDevice device1 = device1Optional.get();
		assertDevice(device1, startTime, deviceName1, operatingSystem1, 0);

		Optional<UserDevice> device2Optional = devices.stream().filter(d -> d.getName().equals(deviceName2)).findAny();
		assertThat(device2Optional.isPresent(), equalTo(true));
		UserDevice device2 = device2Optional.get();
		assertDevice(device2, startTime, deviceName2, operatingSystem2, 1);
	}

	@Test
	public void deleteDevice_deleteOneOfTwo_userHasOneDevice()
	{
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDeviceDto deviceDto1 = new UserDeviceDto(deviceName1, operatingSystem1);
		service.addDeviceToUser(john, deviceDto1);

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDeviceDto deviceDto2 = new UserDeviceDto(deviceName2, operatingSystem2);
		service.addDeviceToUser(john, deviceDto2);

		verify(mockUserDeviceRepository, times(2)).save(any(UserDevice.class));

		Set<UserDevice> devices = john.getDevices();
		assertThat(devices.size(), equalTo(2));

		Optional<UserDevice> device1Optional = devices.stream().filter(d -> d.getName().equals(deviceName1)).findAny();
		assertThat(device1Optional.isPresent(), equalTo(true));
		UserDevice device1 = device1Optional.get();
		assertDevice(device1, startTime, deviceName1, operatingSystem1, 0);

		service.deleteDevice(john, device1);
		assertThat(deviceAnonymizedRepository.count(), equalTo(1L));

		devices = john.getDevices();
		assertThat(devices.size(), equalTo(1));

		UserDevice device2 = devices.iterator().next();
		assertDevice(device2, startTime, deviceName2, operatingSystem2, 1);
	}

	@Test
	public void addDeviceToUser_addAfterDelete_deviceIdReused()
	{
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDeviceDto deviceDto1 = new UserDeviceDto(deviceName1, operatingSystem1);
		service.addDeviceToUser(john, deviceDto1);

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDeviceDto deviceDto2 = new UserDeviceDto(deviceName2, operatingSystem2);
		service.addDeviceToUser(john, deviceDto2);

		verify(mockUserDeviceRepository, times(2)).save(any(UserDevice.class));

		Set<UserDevice> devices = john.getDevices();
		assertThat(devices.size(), equalTo(2));

		Optional<UserDevice> device1Optional = devices.stream().filter(d -> d.getName().equals(deviceName1)).findAny();
		assertThat(device1Optional.isPresent(), equalTo(true));
		UserDevice device1 = device1Optional.get();

		service.deleteDevice(john, device1);
		assertThat(deviceAnonymizedRepository.count(), equalTo(1L));

		devices = john.getDevices();
		assertThat(devices.size(), equalTo(1));

		String deviceName3 = "Third";
		OperatingSystem operatingSystem3 = OperatingSystem.IOS;
		UserDeviceDto deviceDto3 = new UserDeviceDto(deviceName3, operatingSystem3);
		service.addDeviceToUser(john, deviceDto3);

		devices = john.getDevices();
		assertThat(devices.size(), equalTo(2));

		Optional<UserDevice> device2Optional = devices.stream().filter(d -> d.getName().equals(deviceName2)).findAny();
		assertThat(device2Optional.isPresent(), equalTo(true));
		UserDevice device2 = device2Optional.get();
		assertDevice(device2, startTime, deviceName2, operatingSystem2, 1);

		Optional<UserDevice> device3Optional = devices.stream().filter(d -> d.getName().equals(deviceName3)).findAny();
		assertThat(device3Optional.isPresent(), equalTo(true));
		UserDevice device3 = device3Optional.get();
		assertDevice(device3, startTime, deviceName3, operatingSystem3, 0);
	}

	@Test
	public void deleteDevice_tryDeleteOneAndOnlyDevice_exception() throws Exception
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.cannot.delete.last.one"));
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		UserDeviceDto deviceDto = new UserDeviceDto(deviceName, operatingSystem);
		service.addDeviceToUser(john, deviceDto);

		verify(mockUserDeviceRepository, times(1)).save(any(UserDevice.class));

		Set<UserDevice> devices = john.getDevices();
		assertThat(devices.size(), equalTo(1));

		UserDevice device = devices.iterator().next();

		service.deleteDevice(john, device);
	}

	@Test
	public void deleteDevice_tryDeleteNonExistingDevice_exception() throws Exception
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.not.found.id"));

		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDeviceDto deviceDto1 = new UserDeviceDto(deviceName1, operatingSystem1);
		service.addDeviceToUser(john, deviceDto1);

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDeviceDto deviceDto2 = new UserDeviceDto(deviceName2, operatingSystem2);
		service.addDeviceToUser(john, deviceDto2);

		verify(mockUserDeviceRepository, times(2)).save(any(UserDevice.class));

		Set<UserDevice> devices = john.getDevices();
		assertThat(devices.size(), equalTo(2));

		Optional<UserDevice> device1Optional = devices.stream().filter(d -> d.getName().equals(deviceName1)).findAny();
		assertThat(device1Optional.isPresent(), equalTo(true));
		UserDevice device1 = device1Optional.get();
		assertDevice(device1, startTime, deviceName1, operatingSystem1, 0);

		service.deleteDevice(john, device1);
		assertThat(deviceAnonymizedRepository.count(), equalTo(1L));

		service.deleteDevice(john, device1);
	}

	private void assertDevice(UserDevice device, LocalDateTime startTime, String expectedDeviceName,
			OperatingSystem expectedOperatingSystem, int expectedDeviceId)
	{
		assertThat(device.getName(), equalTo(expectedDeviceName));
		assertThat(device.getAppLastOpenedDate(), equalTo(TimeUtil.utcNow().toLocalDate()));
		assertThat(device.getRegistrationTime(), is(between(startTime, TimeUtil.utcNow())));
		assertThat(device.isVpnConnected(), equalTo(true));

		DeviceAnonymized deviceAnonymized = device.getDeviceAnonymized();
		assertThat(deviceAnonymized.getOperatingSystem(), equalTo(expectedOperatingSystem));
		assertThat(deviceAnonymized.getLastMonitoredActivityDate().isPresent(), equalTo(false));
		assertThat(deviceAnonymized.getDeviceId(), equalTo(expectedDeviceId));
		assertThat(deviceAnonymized.getUserAnonymized().getId(), equalTo(john.getAnonymized().getId()));
	}
}