/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.BuddyDeviceChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.LDAPUserService;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class DeviceService
{
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
		UserDevice deviceEntity = userDeviceRepository.findOne(deviceId);
		if (deviceEntity == null)
		{
			throw DeviceServiceException.notFoundById(deviceId);
		}
		return deviceEntity;
	}

	@Transactional
	public UserDeviceDto addDeviceToUser(User userEntity, UserDeviceDto deviceDto)
	{
		assertDeviceNameDoesNotExist(userEntity, deviceDto.getName());
		DeviceAnonymized deviceAnonymized = DeviceAnonymized.createInstance(findFirstFreeDeviceIndex(userEntity),
				deviceDto.getOperatingSystem(), deviceDto.getAppVersion());
		deviceAnonymizedRepository.save(deviceAnonymized);
		UserDevice deviceEntity = userDeviceRepository
				.save(UserDevice.createInstance(deviceDto.getName(), deviceAnonymized.getId(), userService.generatePassword()));
		userEntity.addDevice(deviceEntity);

		ldapUserService.createVpnAccount(buildVpnLoginId(userEntity.getAnonymized(), deviceEntity),
				deviceEntity.getVpnPassword());
		sendNewDeviceMessageToBuddies(userEntity, deviceDto, deviceAnonymized);

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

	private void sendNewDeviceMessageToBuddies(User userEntity, UserDeviceDto deviceDto, DeviceAnonymized deviceAnonymized)
	{
		DeviceChange change = DeviceChange.ADD;
		Optional<String> oldName = Optional.empty();
		Optional<String> newName = Optional.of(deviceDto.getName());
		messageService.broadcastMessageToBuddies(UserAnonymizedDto.createInstance(userEntity.getAnonymized()),
				() -> BuddyDeviceChangeMessage.createInstance(
						BuddyInfoParameters.createInstance(userEntity, userEntity.getNickname()),
						getDeviceChangeMessageText(change, oldName, newName), change, deviceAnonymized.getId(), oldName,
						newName));
	}

	@Transactional
	public UserDeviceDto addDeviceToUser(UUID userId, UserDeviceDto deviceDto)
	{
		return addDeviceToUser(userService.getUserEntityById(userId), deviceDto);
	}

	private void assertDeviceNameDoesNotExist(User userEntity, String name)
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

	public UserDeviceDto createDefaultUserDeviceDto()
	{
		return new UserDeviceDto(translator.getLocalizedMessage("default.device.name"), OperatingSystem.UNKNOWN, "Unknown");
	}

	private int findFirstFreeDeviceIndex(User userEntity)
	{
		Set<Integer> deviceIndexes = userEntity.getDevices().stream().map(UserDevice::getDeviceAnonymized)
				.map(DeviceAnonymized::getDeviceIndex).collect(Collectors.toSet());
		return IntStream.range(0, deviceIndexes.size() + 1).filter(i -> !deviceIndexes.contains(i)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Should always find a nonexisting index"));
	}

	@Transactional
	public void deleteDevice(User userEntity, UserDevice device)
	{
		Set<UserDevice> devices = userEntity.getDevices();
		if (((devices.size() == 1) && devices.contains(device)))
		{
			throw DeviceServiceException.cannotDeleteLastDevice(device.getId());
		}
		deleteDeviceEvenIfLastOne(userEntity, device);
	}

	@Transactional
	public void deleteDeviceEvenIfLastOne(User userEntity, UserDevice device)
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
		deleteDevice(userEntity, deviceToBeRemoved);
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
}