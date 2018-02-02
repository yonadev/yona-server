/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import static com.spencerwi.hamcrestJDK8Time.matchers.IsBetween.between;
import static nu.yona.server.test.util.Matchers.hasMessageId;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.ActivityRepository;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.entities.ActivityRepositoryMock;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.TimeUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.device.service",
		"nu.yona.server.subscriptions.service", "nu.yona.server.properties", "nu.yona.server" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserAnonymizedService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.device.service.DeviceService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.Translator", type = FilterType.REGEX) })
class DeviceServiceTestConfiguration extends UserRepositoriesConfiguration
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

	@Bean
	ActivityRepository getActivityRepository()
	{
		return new ActivityRepositoryMock();
	}
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { DeviceServiceTestConfiguration.class })
public class DeviceServiceTest extends BaseSpringIntegrationTest
{
	@Autowired
	private UserDeviceRepository userDeviceRepository;

	@Autowired
	private ActivityRepository activityRepository;

	@Autowired
	private DeviceService service;

	@Autowired
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@MockBean
	private MessageService mockMessageService;

	private static final String PASSWORD = "password";
	private User richard;

	@Rule
	public MethodRule cryptoSession = new CryptoSessionRule(PASSWORD);

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Before
	public void setUpPerTest()
	{
		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			richard = JUnitUtil.createRichard();
		}
		reset(userRepository);
		reset(userDeviceRepository);
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(DeviceAnonymized.class, deviceAnonymizedRepository);
		repositoriesMap.put(UserDevice.class, userDeviceRepository);
		repositoriesMap.put(Activity.class, activityRepository);
		return repositoriesMap;
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
		UserDeviceDto deviceDto = new UserDeviceDto(deviceName, operatingSystem, "Unknown");
		service.addDeviceToUser(richard, deviceDto);

		verify(userDeviceRepository, times(1)).save(any(UserDevice.class));
		assertThat(deviceAnonymizedRepository.count(), equalTo(1L));

		Set<UserDevice> devices = richard.getDevices();
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
		richard.addDevice(createDevice(0, deviceName1, operatingSystem1, "Unknown"));

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDeviceDto deviceDto2 = new UserDeviceDto(deviceName2, operatingSystem2, "Unknown");
		service.addDeviceToUser(richard, deviceDto2);

		verify(userDeviceRepository, times(2)).save(any(UserDevice.class));
		assertThat(deviceAnonymizedRepository.count(), equalTo(2L));

		Set<UserDevice> devices = richard.getDevices();
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
	public void addDeviceToUser_tryAddDuplicateName_exception()
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.name.already.exists"));

		String deviceName = "First";
		richard.addDevice(createDevice(0, deviceName, OperatingSystem.ANDROID, "Unknown"));

		UserDeviceDto deviceDto2 = new UserDeviceDto(deviceName, OperatingSystem.IOS, "Unknown");
		service.addDeviceToUser(richard, deviceDto2);
	}

	@Test
	public void deleteDevice_deleteOneOfTwo_userHasOneDevice()
	{
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDevice device1 = createDevice(0, "First", OperatingSystem.ANDROID, "Unknown");
		richard.addDevice(device1);

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		richard.addDevice(createDevice(1, deviceName2, operatingSystem2, "Unknown"));

		assertThat(richard.getDevices().size(), equalTo(2));

		service.deleteDevice(richard, device1);
		assertThat(deviceAnonymizedRepository.count(), equalTo(1L));

		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.size(), equalTo(1));

		UserDevice device2 = devices.iterator().next();
		assertDevice(device2, startTime, deviceName2, operatingSystem2, 1);
	}

	@Test
	public void addDeviceToUser_addAfterDelete_deviceIdReused()
	{
		LocalDateTime startTime = TimeUtil.utcNow();
		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		richard.addDevice(createDevice(1, deviceName2, operatingSystem2, "Unknown"));

		assertThat(richard.getDevices().size(), equalTo(1));

		String deviceName3 = "Third";
		OperatingSystem operatingSystem3 = OperatingSystem.IOS;
		UserDeviceDto deviceDto3 = new UserDeviceDto(deviceName3, operatingSystem3, "Unknown");
		service.addDeviceToUser(richard, deviceDto3);

		Set<UserDevice> devices = richard.getDevices();
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
	public void addDeviceToUser_byId_addFirstDevice_userHasOneDevice()
	{
		String deviceName = "Testing";
		OperatingSystem operatingSystem = OperatingSystem.ANDROID;
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDeviceDto deviceDto = new UserDeviceDto(deviceName, operatingSystem, "Unknown");
		service.addDeviceToUser(richard.getId(), deviceDto);

		verify(userDeviceRepository, times(1)).save(any(UserDevice.class));
		assertThat(deviceAnonymizedRepository.count(), equalTo(1L));

		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.size(), equalTo(1));

		UserDevice device = devices.iterator().next();
		assertDevice(device, startTime, deviceName, operatingSystem, 0);
	}

	@Test
	public void deleteDevice_tryDeleteOneAndOnlyDevice_exception() throws Exception
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.cannot.delete.last.one"));

		richard.addDevice(createDevice(0, "Testing", OperatingSystem.ANDROID, "Unknown"));

		assertThat(richard.getDevices().size(), equalTo(1));

		UserDevice device = richard.getDevices().iterator().next();

		service.deleteDevice(richard, device);
	}

	@Test
	public void deleteDevice_tryDeleteNonExistingDevice_exception() throws Exception
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.not.found.id"));

		richard.addDevice(createDevice(0, "First", OperatingSystem.ANDROID, "Unknown"));
		UserDevice notAddedDevice = createDevice(1, "NotAddedDevice", OperatingSystem.IOS, "Unknown");

		assertThat(richard.getDevices().size(), equalTo(1));

		service.deleteDevice(richard, notAddedDevice);
	}

	private void assertDevice(UserDevice device, LocalDateTime startTime, String expectedDeviceName,
			OperatingSystem expectedOperatingSystem, int expectedDeviceIndex)
	{
		assertThat(device.getName(), equalTo(expectedDeviceName));
		assertThat(device.getAppLastOpenedDate(), equalTo(TimeUtil.utcNow().toLocalDate()));
		assertThat(device.getRegistrationTime(), is(between(startTime, TimeUtil.utcNow())));
		assertThat(device.isVpnConnected(), equalTo(true));

		DeviceAnonymized deviceAnonymized = device.getDeviceAnonymized();
		assertThat(deviceAnonymized.getOperatingSystem(), equalTo(expectedOperatingSystem));
		assertThat(deviceAnonymized.getLastMonitoredActivityDate().isPresent(), equalTo(false));
		assertThat(deviceAnonymized.getDeviceIndex(), equalTo(expectedDeviceIndex));
		assertThat(deviceAnonymized.getUserAnonymized().getId(), equalTo(richard.getAnonymized().getId()));
	}

	@Test
	public void getDefaultDeviceId_oneDevice_deviceReturned()
	{
		UserDevice device = createDevice(0, "First", OperatingSystem.ANDROID, "Unknown");
		richard.addDevice(device);

		UUID defaultDeviceId = service.getDefaultDeviceId(createRichardUserDto());

		assertThat(defaultDeviceId, equalTo(device.getId()));
	}

	@Test
	public void getDefaultDeviceId_twoDevices_firstDeviceReturned()
	{
		UserDevice device1 = createDevice(0, "First", OperatingSystem.ANDROID, "Unknown");
		richard.addDevice(device1);
		UserDevice device2 = createDevice(1, "Second", OperatingSystem.IOS, "Unknown");
		richard.addDevice(device2);

		UUID defaultDeviceId = service.getDefaultDeviceId(createRichardUserDto());

		assertThat(defaultDeviceId, equalTo(device1.getId()));
	}

	@Test
	public void getDefaultDeviceId_tryNoDevices_exception() throws Exception
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.collection.empty"));

		service.getDefaultDeviceId(createRichardUserDto());
	}

	@Test
	public void getDeviceAnonymized_byIndex_firstDevice_correctDevice()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		UserDevice device1 = addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Get the anonymized device for the first index
		DeviceAnonymizedDto deviceAnonymized = service.getDeviceAnonymized(createRichardAnonymizedDto(), 0);

		// Assert success
		assertThat(deviceAnonymized.getId(), equalTo(userDeviceRepository.getOne(device1.getId()).getDeviceAnonymizedId()));
	}

	@Test
	public void getDeviceAnonymized_byIndex_secondDevice_correctDevice()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDevice device2 = addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Get the anonymized device for the second index
		DeviceAnonymizedDto deviceAnonymized = service.getDeviceAnonymized(createRichardAnonymizedDto(), 1);

		// Assert success
		assertThat(deviceAnonymized.getId(), equalTo(userDeviceRepository.getOne(device2.getId()).getDeviceAnonymizedId()));
	}

	@Test
	public void getDeviceAnonymized_byIndex_defaultDevice_correctDevice()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		UserDevice device1 = addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Get the anonymized device for a negative index, implies fall back to default device
		DeviceAnonymizedDto deviceAnonymized = service.getDeviceAnonymized(createRichardAnonymizedDto(), -1);

		// Assert success
		assertThat(deviceAnonymized.getId(), equalTo(userDeviceRepository.getOne(device1.getId()).getDeviceAnonymizedId()));
	}

	@Test
	public void getDeviceAnonymized_byIndex_tryGetNonExistingIndex_exception()
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.not.found.index"));

		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Try to get the anonymized ID for a nonexisting index
		service.getDeviceAnonymized(createRichardAnonymizedDto(), 2);
	}

	@Test
	public void getDeviceAnonymized_byId_firstDevice_correctDevice()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		UserDevice device1 = addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");
		UUID deviceAnonymizedId1 = device1.getDeviceAnonymizedId();

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Get the anonymized device for the first ID
		DeviceAnonymizedDto deviceAnonymized = service.getDeviceAnonymized(createRichardAnonymizedDto(), deviceAnonymizedId1);

		// Assert success
		assertThat(deviceAnonymized.getId(), equalTo(userDeviceRepository.getOne(device1.getId()).getDeviceAnonymizedId()));
	}

	@Test
	public void getDeviceAnonymized_byId_secondDevice_correctDevice()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDevice device2 = addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");
		UUID deviceAnonymizedId2 = device2.getDeviceAnonymizedId();

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Get the anonymized device for the second ID
		DeviceAnonymizedDto deviceAnonymized = service.getDeviceAnonymized(createRichardAnonymizedDto(), deviceAnonymizedId2);

		// Assert success
		assertThat(deviceAnonymized.getId(), equalTo(userDeviceRepository.getOne(device2.getId()).getDeviceAnonymizedId()));
	}

	@Test
	public void getDeviceAnonymized_byId_tryGetNonExistingId_exception()
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.not.found.anonymized.id"));

		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Try to get the anonymized ID for a nonexisting ID
		service.getDeviceAnonymized(createRichardAnonymizedDto(), UUID.randomUUID());
	}

	@Test
	public void getDeviceAnonymizedId_byId_firstDevice_correctDevice()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		UserDevice device1 = addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Get the anonymized ID for the first device ID
		UUID deviceAnonymizedId = service.getDeviceAnonymizedId(createRichardUserDto(), device1.getId());

		// Assert success
		assertThat(deviceAnonymizedId, equalTo(userDeviceRepository.getOne(device1.getId()).getDeviceAnonymizedId()));
	}

	@Test
	public void getDeviceAnonymizedId_byId_secondDevice_correctDevice()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		UserDevice device2 = addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Get the anonymized ID for the second device ID
		UUID deviceAnonymizedId = service.getDeviceAnonymizedId(createRichardUserDto(), device2.getId());

		// Assert success
		assertThat(deviceAnonymizedId, equalTo(userDeviceRepository.getOne(device2.getId()).getDeviceAnonymizedId()));
	}

	@Test
	public void getDeviceAnonymizedId_byId_tryGetNonExistingId_exception()
	{
		expectedException.expect(DeviceServiceException.class);
		expectedException.expect(hasMessageId("error.device.not.found.id"));

		// Add device2
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		addDeviceToRichard(1, deviceName2, operatingSystem2, "Unknown");

		UserDevice notAddedDevice = createDevice(2, "NotAddedDevice", OperatingSystem.IOS, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		// Try to get the anonymized ID for a nonexisting device ID
		service.getDeviceAnonymizedId(createRichardUserDto(), notAddedDevice.getId());
	}

	@Test
	public void removeDuplicateDefaultDevices_singleDevice_noChange()
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		UserDevice device1 = addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()), containsInAnyOrder(deviceName1));

		service.removeDuplicateDefaultDevices(createRichardUserDto(), device1.getId());

		// Assert success
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()), containsInAnyOrder(deviceName1));
	}

	@Test
	public void removeDuplicateDefaultDevices_twoDevicesUseFirst_duplicateRemovedActivitiesMoved()
	{
		removeDuplicateDefaultDevicesFirstOrSecond(0);
	}

	@Test
	public void removeDuplicateDefaultDevices_twoDevicesUseSecond_duplicateRemovedActivitiesMoved()
	{
		removeDuplicateDefaultDevicesFirstOrSecond(1);
	}

	private void removeDuplicateDefaultDevicesFirstOrSecond(int indexToRetain)
	{
		// Add devices
		String deviceName1 = "First";
		OperatingSystem operatingSystem1 = OperatingSystem.ANDROID;
		LocalDateTime startTime = TimeUtil.utcNow();
		UserDevice device1 = addDeviceToRichard(0, deviceName1, operatingSystem1, "Unknown");
		ActivityData activity1 = new ActivityData("WhatsApp", 10, 2);
		ActivityData activity2 = new ActivityData("WhatsApp", 5, 1);
		Set<Activity> device1Activities = addActivities(device1, startTime, activity1, activity2);

		String deviceName2 = "Second";
		OperatingSystem operatingSystem2 = OperatingSystem.IOS;
		ActivityData activity3 = new ActivityData("WhatsApp", 9, 2);
		ActivityData activity4 = new ActivityData("WhatsApp", 3, 1);
		UserDevice device2 = addDeviceToRichard(0, deviceName2, operatingSystem2, "Unknown");
		Set<Activity> device2Activities = addActivities(device2, startTime, activity3, activity4);

		List<UserDevice> createdDevices = Arrays.asList(device1, device2);

		// Verify two devices are present
		Set<UserDevice> devices = richard.getDevices();
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(deviceName1, deviceName2));

		service.removeDuplicateDefaultDevices(createRichardUserDto(), createdDevices.get(indexToRetain).getId());

		// Assert success
		assertThat(devices.stream().map(UserDevice::getName).collect(Collectors.toSet()),
				containsInAnyOrder(createdDevices.get(indexToRetain).getName()));
		device1Activities.forEach(a -> assertThat(a.getDeviceAnonymized().get(),
				sameInstance(createdDevices.get(indexToRetain).getDeviceAnonymized())));
		device2Activities.forEach(a -> assertThat(a.getDeviceAnonymized().get(),
				sameInstance(createdDevices.get(indexToRetain).getDeviceAnonymized())));
	}

	private Set<Activity> addActivities(UserDevice deviceEntity, LocalDateTime startTime, ActivityData... activityData)
	{
		return Arrays.asList(activityData).stream().map(ad -> makeActivity(startTime, ad, deviceEntity))
				.collect(Collectors.toSet());
	}

	private UserDevice createDevice(int deviceIndex, String deviceName, OperatingSystem operatingSystem, String appVersion)
	{
		DeviceAnonymized deviceAnonymized = DeviceAnonymized.createInstance(deviceIndex, operatingSystem, appVersion);
		UserDevice device = UserDevice.createInstance(deviceName, deviceAnonymized.getId());
		deviceAnonymizedRepository.save(deviceAnonymized);
		userDeviceRepository.save(device);
		return device;
	}

	private UserDto createRichardUserDto()
	{
		return UserDto.createInstanceWithPrivateData(richard, Collections.emptySet());
	}

	private UserAnonymizedDto createRichardAnonymizedDto()
	{
		return UserAnonymizedDto.createInstance(richard.getAnonymized());
	}

	private UserDevice addDeviceToRichard(int deviceIndex, String deviceName, OperatingSystem operatingSystem, String appVersion)
	{
		UserDevice deviceEntity = createDevice(deviceIndex, deviceName, operatingSystem, appVersion);
		richard.addDevice(deviceEntity);
		return deviceEntity;
	}

	private Activity makeActivity(LocalDateTime startTime, ActivityData activityData, UserDevice device)
	{
		LocalDateTime activityStartTime = startTime.minusMinutes(activityData.minutesAgo);
		return activityRepository.save(Activity.createInstance(device.getDeviceAnonymized(), ZoneId.of("Europe/Amsterdam"),
				activityStartTime, activityStartTime.plusMinutes(activityData.durationMinutes), Optional.of(activityData.app)));
	}

	private static class ActivityData
	{
		final String app;
		final int minutesAgo;
		final int durationMinutes;

		public ActivityData(String app, int minutesAgo, int durationMinutes)
		{
			this.app = app;
			this.minutesAgo = minutesAgo;
			this.durationMinutes = durationMinutes;
		}
	}
}