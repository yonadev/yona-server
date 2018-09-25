/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.Translator;
import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.ActivityRepository;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.DeviceBase;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.device.entities.VpnStatusChangeEvent;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.BuddyDeviceChangeMessage;
import nu.yona.server.subscriptions.entities.BuddyVpnConnectionStatusChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.LDAPUserService;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.TimeUtil;

@Service
public class DeviceService
{
	// When updating the minimum version, be sure to update the version code and the version.
	// Minimal technical version codes, used to verify the minimum
	private static final int ANDROID_MIN_APP_VERSION_CODE = 5;
	private static final int IOS_MIN_APP_VERSION_CODE = ANDROID_MIN_APP_VERSION_CODE;
	// User-friendly minimum version, used for error message
	private static final String ANDROID_MIN_APP_VERSION = "1.0.1";
	private static final String IOS_MIN_APP_VERSION = ANDROID_MIN_APP_VERSION;

	@Autowired(required = false)
	private UserService userService;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	private LDAPUserService ldapUserService;

	@Autowired(required = false)
	private MessageService messageService;

	@Autowired(required = false)
	private UserDeviceRepository userDeviceRepository;

	@Autowired(required = false)
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@Autowired
	private Translator translator;

	@Autowired(required = false)
	private ActivityRepository activityRepository;

	@Transactional
	public Set<DeviceBaseDto> getDevicesOfUser(UUID userId)
	{
		return userService.getUserEntityById(userId).getDevices().stream().map(UserDeviceDto::createInstance)
				.collect(Collectors.toSet());
	}

	@Transactional
	public UserDeviceDto getDevice(UUID deviceId)
	{
		return UserDeviceDto.createInstance(getDeviceEntity(deviceId));
	}

	private UserDevice getDeviceEntity(UUID deviceId)
	{
		return userDeviceRepository.findById(deviceId).orElseThrow(() -> DeviceServiceException.notFoundById(deviceId));
	}

	@Transactional
	public UserDeviceDto addDeviceToUser(User userEntity, UserDeviceDto deviceDto)
	{
		assertAcceptableDeviceName(userEntity, deviceDto.getName());
		assertValidAppVersion(deviceDto.getOperatingSystem(), deviceDto.getAppVersion(), deviceDto.getAppVersionCode());
		DeviceAnonymized deviceAnonymized = DeviceAnonymized.createInstance(findFirstFreeDeviceIndex(userEntity),
				deviceDto.getOperatingSystem(), deviceDto.getAppVersion(), deviceDto.getAppVersionCode(),
				deviceDto.getFirebaseInstanceId());
		deviceAnonymizedRepository.save(deviceAnonymized);
		UserDevice deviceEntity = userDeviceRepository
				.save(UserDevice.createInstance(deviceDto.getName(), deviceAnonymized.getId(), userService.generatePassword()));
		userEntity.addDevice(deviceEntity);

		ldapUserService.createVpnAccount(buildVpnLoginId(userEntity.getAnonymized(), deviceEntity),
				deviceEntity.getVpnPassword());
		sendDeviceChangeMessageToBuddies(DeviceChange.ADD, userEntity, Optional.empty(), Optional.of(deviceDto.getName()),
				deviceAnonymized.getId());
		return UserDeviceDto.createInstance(deviceEntity);
	}

	public static String buildVpnLoginId(UserAnonymized userAnonymizedEntity, UserDevice deviceEntity)
	{
		String legacyVpnLoginId = userAnonymizedEntity.getId().toString();
		if (deviceEntity.isLegacyVpnAccount())
		{
			return legacyVpnLoginId;
		}
		return legacyVpnLoginId + "$" + deviceEntity.getDeviceAnonymized().getDeviceIndex();
	}

	private void sendDeviceChangeMessageToBuddies(DeviceChange change, User userEntity, Optional<String> oldName,
			Optional<String> newName, UUID deviceAnonymizedId)
	{
		messageService.broadcastMessageToBuddies(UserAnonymizedDto.createInstance(userEntity.getAnonymized()),
				() -> BuddyDeviceChangeMessage.createInstance(
						BuddyInfoParameters.createInstance(userEntity, userEntity.getNickname()),
						getDeviceChangeMessageText(change, oldName, newName), change, deviceAnonymizedId, oldName, newName));
	}

	@Transactional
	public UserDeviceDto addDeviceToUser(UUID userId, UserDeviceDto deviceDto)
	{
		return addDeviceToUser(userService.getUserEntityById(userId), deviceDto);
	}

	private static void assertAcceptableDeviceName(User userEntity, String deviceName)
	{
		assertValidDeviceName(deviceName);
		assertDeviceNameDoesNotExist(userEntity, deviceName);
	}

	private static void assertValidDeviceName(String name)
	{
		if (name.length() > DeviceBase.MAX_NAME_LENGTH || name.contains(DeviceBase.DEVICE_NAMES_SEPARATOR))
		{
			throw InvalidDataException.invalidDeviceName(name, DeviceBase.MAX_NAME_LENGTH, DeviceBase.DEVICE_NAMES_SEPARATOR);
		}
	}

	private static void assertDeviceNameDoesNotExist(User userEntity, String name)
	{
		if (userEntity.getDevices().stream().map(UserDevice::getName).anyMatch(n -> n.equals(name)))
		{
			throw DeviceServiceException.duplicateDeviceName(name);
		}
	}

	private String getDeviceChangeMessageText(DeviceChange change, Optional<String> oldName, Optional<String> newName)
	{
		switch (change)
		{
			case ADD:
				return translator.getLocalizedMessage("message.buddy.device.added", newName.get());
			case RENAME:
				return translator.getLocalizedMessage("message.buddy.device.renamed", oldName.get(), newName.get());
			case DELETE:
				return translator.getLocalizedMessage("message.buddy.device.deleted", oldName.get());
			default:
				throw new IllegalArgumentException("Change " + change + " is unknown");
		}
	}

	@Transactional
	public UserDeviceDto updateDevice(UUID userId, UUID deviceId, DeviceUpdateRequestDto changeRequest)
	{
		userService.updateUser(userId, userEntity -> {
			UserDevice deviceEntity = getDeviceEntity(deviceId);

			String oldName = deviceEntity.getName();
			if (!oldName.equals(changeRequest.name))
			{
				assertAcceptableDeviceName(userEntity, changeRequest.name);
				deviceEntity.setName(changeRequest.name);
				userDeviceRepository.save(deviceEntity);
				sendDeviceChangeMessageToBuddies(DeviceChange.RENAME, userEntity, Optional.of(oldName),
						Optional.of(changeRequest.name), deviceEntity.getDeviceAnonymized().getId());
			}

			DeviceAnonymized deviceAnonymized = deviceEntity.getDeviceAnonymized();
			Optional<String> oldFirebaseInstanceId = deviceAnonymized.getFirebaseInstanceId();
			if (changeRequest.firebaseInstanceId.isPresent() && !oldFirebaseInstanceId.equals(changeRequest.firebaseInstanceId))
			{
				deviceAnonymized.setFirebaseInstanceId(changeRequest.firebaseInstanceId.get());
				deviceAnonymizedRepository.save(deviceAnonymized);
			}
		});
		return getDevice(deviceId);
	}

	public UserDeviceDto createDefaultUserDeviceDto()
	{
		return new UserDeviceDto(translator.getLocalizedMessage("default.device.name"), OperatingSystem.UNKNOWN, "Unknown", 0,
				Optional.empty());
	}

	private int findFirstFreeDeviceIndex(User userEntity)
	{
		Set<Integer> deviceIndexes = userEntity.getDevices().stream().map(UserDevice::getDeviceAnonymized)
				.map(DeviceAnonymized::getDeviceIndex).collect(Collectors.toSet());
		return IntStream.range(0, deviceIndexes.size() + 1).filter(i -> !deviceIndexes.contains(i)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Should always find a nonexisting index"));
	}

	@Transactional
	public void deleteDevice(UUID userId, UUID deviceId)
	{
		userService.updateUser(userId, userEntity -> {
			UserDevice deviceEntity = getDeviceEntity(deviceId);
			Set<UserDevice> devices = userEntity.getDevices();
			if ((devices.size() == 1) && devices.contains(deviceEntity))
			{
				throw DeviceServiceException.cannotDeleteLastDevice(deviceEntity.getId());
			}

			String oldName = deviceEntity.getName();
			UUID deviceAnonymizedId = deviceEntity.getDeviceAnonymized().getId();

			deleteDeviceWithoutBuddyNotification(userEntity, deviceEntity);

			sendDeviceChangeMessageToBuddies(DeviceChange.DELETE, userEntity, Optional.of(oldName), Optional.empty(),
					deviceAnonymizedId);
		});
	}

	@Transactional
	public void deleteDeviceWithoutBuddyNotification(User userEntity, UserDevice device)
	{
		Set<UserDevice> devices = userEntity.getDevices();
		if (!devices.contains(device))
		{
			throw DeviceServiceException.notFoundById(device.getId());
		}
		ldapUserService.deleteVpnAccount(buildVpnLoginId(userEntity.getAnonymized(), device));

		deviceAnonymizedRepository.delete(device.getDeviceAnonymized());
		deviceAnonymizedRepository.flush(); // Without this, the delete doesn't always occur
		userEntity.removeDevice(device);
	}

	public UUID getDefaultDeviceId(UserDto userDto)
	{
		UserAnonymizedDto userAnonymized = userAnonymizedService
				.getUserAnonymized(userDto.getOwnPrivateData().getUserAnonymizedId());
		UUID defaultDeviceAnonymizedId = getDefaultDeviceAnonymized(userAnonymized).getId();
		return userDto.getOwnPrivateData().getOwnDevices().stream()
				.filter(d -> d.getDeviceAnonymizedId().equals(defaultDeviceAnonymizedId)).map(DeviceBaseDto::getId).findAny()
				.orElseThrow(() -> DeviceServiceException.noDevicesFound(userAnonymized.getId()));
	}

	private DeviceAnonymizedDto getDefaultDeviceAnonymized(UserAnonymizedDto userAnonymized)
	{
		return userAnonymized.getDevicesAnonymized().stream()
				.sorted((d1, d2) -> Integer.compare(d1.getDeviceIndex(), d2.getDeviceIndex())).findFirst()
				.orElseThrow(() -> DeviceServiceException.noDevicesFound(userAnonymized.getId()));
	}

	public UUID getDeviceAnonymizedId(UserDto userDto, UUID deviceId)
	{
		return userService.getUserEntityById(userDto.getId()).getDevices().stream().filter(d -> d.getId().equals(deviceId))
				.findAny().map(UserDevice::getDeviceAnonymizedId)
				.orElseThrow(() -> DeviceServiceException.notFoundById(deviceId));
	}

	public DeviceAnonymizedDto getDeviceAnonymized(UserAnonymizedDto userAnonymized, int deviceIndex)
	{
		return deviceIndex < 0 ? getDefaultDeviceAnonymized(userAnonymized)
				: userAnonymized.getDevicesAnonymized().stream().filter(d -> d.getDeviceIndex() == deviceIndex).findAny()
						.orElseThrow(() -> DeviceServiceException.notFoundByIndex(userAnonymized.getId(), deviceIndex));
	}

	public DeviceAnonymizedDto getDeviceAnonymized(UserAnonymizedDto userAnonymized, UUID deviceAnonymizedId)
	{
		return userAnonymized.getDevicesAnonymized().stream().filter(d -> d.getId().equals(deviceAnonymizedId)).findAny()
				.orElseThrow(() -> DeviceServiceException.notFoundByAnonymizedId(userAnonymized.getId(), deviceAnonymizedId));
	}

	@Transactional
	public void removeDuplicateDefaultDevices(UserDto userDto, UUID requestingDeviceId)
	{
		userService.updateUser(userDto.getId(), userEntity -> {
			Set<UserDevice> defaultDevices = userEntity.getDevices().stream()
					.filter(d -> d.getDeviceAnonymized().getDeviceIndex() == 0).collect(Collectors.toSet());
			if (defaultDevices.isEmpty())
			{
				throw DeviceServiceException.noDevicesFound(userEntity.getUserAnonymizedId());
			}
			if (defaultDevices.size() == 1)
			{
				// User is in good shape
				return;
			}
			UserDevice requestingDevice = userEntity.getDevices().stream().filter(d -> d.getId().equals(requestingDeviceId))
					.findFirst().orElseThrow(() -> DeviceServiceException.notFoundById(requestingDeviceId));
			defaultDevices.stream().filter(d -> d != requestingDevice)
					.forEach(d -> removeDuplicateDefaultDevice(userEntity, requestingDevice, d));
			userAnonymizedService.updateUserAnonymized(userEntity.getAnonymized().getId());
		});
	}

	private void removeDuplicateDefaultDevice(User userEntity, UserDevice requestingDevice, UserDevice deviceToBeRemoved)
	{
		Set<Activity> activitiesOnDevice = activityRepository.findByDeviceAnonymized(deviceToBeRemoved.getDeviceAnonymized());
		activitiesOnDevice.forEach(a -> a.setDeviceAnonymized(requestingDevice.getDeviceAnonymized()));
		deleteDeviceWithoutBuddyNotification(userEntity, deviceToBeRemoved);
	}

	@Transactional
	public void updateOperatingSystem(UUID userId, UUID deviceId, OperatingSystem operatingSystem)
	{
		userService.updateUser(userId, userEntity -> {
			DeviceAnonymized deviceAnonymized = getDeviceEntity(deviceId).getDeviceAnonymized();
			deviceAnonymized.setOperatingSystem(operatingSystem);
			deviceAnonymizedRepository.save(deviceAnonymized);
		});
	}

	@Transactional
	public void postOpenAppEvent(UUID userId, UUID deviceId, Optional<OperatingSystem> operatingSystem,
			Optional<String> appVersion, int appVersionCode)
	{
		userService.updateUser(userId, userEntity -> {
			operatingSystem.ifPresent(os -> assertValidAppVersion(os, appVersion.get(), appVersionCode));

			LocalDate now = TimeUtil.utcNow().toLocalDate();
			UserDevice deviceEntity = getDeviceEntity(deviceId);
			DeviceAnonymized deviceAnonymizedEntity = deviceEntity.getDeviceAnonymized();

			setAppLastOpenedDateIfNewer(now, userEntity::getAppLastOpenedDate, () -> userEntity.setAppLastOpenedDate(now));
			setAppLastOpenedDateIfNewer(now, () -> Optional.of(deviceEntity.getAppLastOpenedDate()),
					() -> deviceEntity.setAppLastOpenedDate(now));
			operatingSystem.ifPresent(os -> setOperatingSystemIfWasUnknown(deviceEntity, os));
			setAppVersionIfDifferent(deviceAnonymizedEntity, appVersion, appVersionCode);
		});
	}

	private void setAppLastOpenedDateIfNewer(LocalDate now, Supplier<Optional<LocalDate>> currentDateSupplier,
			Runnable dateSetter)
	{
		Optional<LocalDate> appLastOpenedDate = currentDateSupplier.get();
		if (!appLastOpenedDate.isPresent() || appLastOpenedDate.get().isBefore(now))
		{
			dateSetter.run();
		}
	}

	private void setOperatingSystemIfWasUnknown(UserDevice deviceEntity, OperatingSystem currentOperatingSystem)
	{
		DeviceAnonymized deviceAnonymized = deviceEntity.getDeviceAnonymized();
		OperatingSystem registeredOperatingSystem = deviceAnonymized.getOperatingSystem();
		if (registeredOperatingSystem != currentOperatingSystem && registeredOperatingSystem != OperatingSystem.UNKNOWN)
		{
			throw DeviceServiceException.cannotSwitchDeviceOperatingSystem(registeredOperatingSystem, currentOperatingSystem,
					deviceEntity.getId());
		}
		deviceAnonymized.setOperatingSystem(currentOperatingSystem);
	}

	private void setAppVersionIfDifferent(DeviceAnonymized deviceAnonymizedEntity, Optional<String> appVersion,
			int appVersionCode)
	{
		if (deviceAnonymizedEntity.getAppVersionCode() != appVersionCode)
		{
			deviceAnonymizedEntity.updateAppVersion(appVersion.get(), appVersionCode);
		}
	}

	private void assertValidAppVersion(OperatingSystem operatingSystem, String appVersion, int appVersionCode)
	{
		int minAppVersionCode;
		String minAppVersion;
		switch (operatingSystem)
		{
			case ANDROID:
				minAppVersionCode = ANDROID_MIN_APP_VERSION_CODE;
				minAppVersion = ANDROID_MIN_APP_VERSION;
				break;
			case IOS:
				minAppVersionCode = IOS_MIN_APP_VERSION_CODE;
				minAppVersion = IOS_MIN_APP_VERSION;
				break;
			case UNKNOWN:
				// Everything is OK for as long as the app didn't confirm the operating system
				return;
			default:
				throw new IllegalArgumentException("Unknown operating system: " + operatingSystem);
		}
		assertNotOlderThan(minAppVersion, minAppVersionCode, operatingSystem, appVersion, appVersionCode);
	}

	private void assertNotOlderThan(String minAppVersion, int minAppVersionCode, OperatingSystem operatingSystem,
			String appVersion, int appVersionCode)
	{
		assertPositiveVersionCode(appVersionCode);
		if (appVersionCode < minAppVersionCode)
		{
			throw DeviceServiceException.appVersionNotSupported(operatingSystem, minAppVersion, appVersion);
		}
	}

	private void assertPositiveVersionCode(int appVersionCode)
	{
		if (appVersionCode < 0)
		{
			throw DeviceServiceException.invalidVersionCode(appVersionCode);
		}
	}

	public void registerVpnStatusChangeEvent(UUID userId, UUID deviceId, boolean isVpnConnected)
	{
		UserDevice deviceEntity = getDeviceEntity(deviceId);
		if (deviceEntity.isVpnConnected() != isVpnConnected)
		{
			userService.updateUser(userId, userEntity -> {
				VpnStatusChangeEvent event = VpnStatusChangeEvent.createInstance(isVpnConnected);
				deviceEntity.getDeviceAnonymized().addVpnStatusChangeEvent(event);
				sendVpnStatusChangeMessageToBuddies(isVpnConnected, userEntity, deviceEntity);
			});
		}
	}

	private void sendVpnStatusChangeMessageToBuddies(boolean isVpnConnected, User userEntity, UserDevice deviceEntity)
	{
		messageService.broadcastMessageToBuddies(UserAnonymizedDto.createInstance(userEntity.getAnonymized()),
				() -> BuddyVpnConnectionStatusChangeMessage.createInstance(
						BuddyInfoParameters.createInstance(userEntity, userEntity.getNickname()),
						getVpnStatusChangeMessageText(isVpnConnected, deviceEntity.getName()), isVpnConnected,
						deviceEntity.getDeviceAnonymizedId()));
	}

	private String getVpnStatusChangeMessageText(boolean isVpnConnected, String deviceName)
	{
		return (isVpnConnected) ? translator.getLocalizedMessage("message.buddy.device.vpn.connect", deviceName)
				: translator.getLocalizedMessage("message.buddy.device.vpn.disconnect", deviceName);
	}
}