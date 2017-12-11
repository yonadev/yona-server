/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;

@Component
@Order(1) // Never change the order or remove a migration step. If the step is obsolete, make it empty
public class AddFirstDevice implements MigrationStep
{
	@Autowired
	private DeviceService deviceService;

	@Override
	public void upgrade(User user)
	{
		Set<UserDevice> devices = user.getDevices();
		if (devices.isEmpty())
		{
			createInitialDevice(user);
		}
		else
		{
			addDevicesAnonymizedToUserAnonymizedIfNotDoneYet(user);
		}
	}

	private void createInitialDevice(User user)
	{
		deviceService.addDeviceToUser(user, deviceService.createDefaultUserDeviceDto());
	}

	private void addDevicesAnonymizedToUserAnonymizedIfNotDoneYet(User user)
	{
		UserAnonymized userAnonymized = user.getAnonymized();
		Set<UserDevice> devices = user.getDevices();
		devices.stream().map(UserDevice::getDeviceAnonymized)
				.forEach(d -> addDeviceAnonymizedToUserAnonymizedIfNotDoneYet(userAnonymized, d));
	}

	private void addDeviceAnonymizedToUserAnonymizedIfNotDoneYet(UserAnonymized userAnonymized, DeviceAnonymized deviceAnonimized)
	{
		if (!userAnonymized.getDevicesAnonymized().contains(deviceAnonimized))
		{
			userAnonymized.addDeviceAnonymized(deviceAnonimized);
			UserAnonymized.getRepository().save(userAnonymized);
		}
	}
}
