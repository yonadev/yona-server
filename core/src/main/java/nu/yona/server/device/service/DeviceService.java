/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.device.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class DeviceService
{
	@Autowired(required = false)
	private UserService userService;

	@Autowired(required = false)
	private UserDeviceRepository userDeviceRepository;

	@Autowired(required = false)
	private DeviceAnonymizedRepository deviceAnonymizedRepository;

	@Transactional
	public Set<DeviceBaseDto> getDevicesOfUser(UUID userId)
	{
		return userService.getUserEntityById(userId).getDevices().stream().map(UserDeviceDto::createInstance)
				.collect(Collectors.toSet());
	}

	@Transactional
	public DeviceBaseDto getDevice(UUID deviceId)
	{
		UserDevice deviceEntity = userDeviceRepository.getOne(deviceId);
		if (deviceEntity == null)
		{
			throw DeviceServiceException.notFoundById(deviceId);
		}
		return UserDeviceDto.createInstance(deviceEntity);
	}

	@Transactional
	public void addDeviceToUser(User userEntity, UserDeviceDto deviceDto)
	{
		DeviceAnonymized deviceAnonymized = new DeviceAnonymized(UUID.randomUUID(), findFirstFreeDeviceId(userEntity),
				deviceDto.getOperatingSystem());
		deviceAnonymizedRepository.save(deviceAnonymized);
		userEntity.addDevice(
				userDeviceRepository.save(new UserDevice(UUID.randomUUID(), deviceDto.getName(), deviceAnonymized.getId())));
	}

	private int findFirstFreeDeviceId(User userEntity)
	{
		Set<Integer> deviceIds = userEntity.getDevices().stream().map(UserDevice::getDeviceAnonymized)
				.map(DeviceAnonymized::getDeviceId).collect(Collectors.toSet());
		return IntStream.range(0, deviceIds.size() + 1).filter(i -> !deviceIds.contains(i)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Should always find a nonexisting ID"));
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
		deviceAnonymizedRepository.delete(device.getDeviceAnonymized());
		userEntity.removeDevice(device);
	}
}