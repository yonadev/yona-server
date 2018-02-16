/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import nu.yona.server.device.entities.UserDevice;
import nu.yona.server.device.service.DeviceServiceException;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;

@Component
@Order(2) // Never change the order or remove a migration step. If the step is obsolete, make it empty
public class MoveVpnPasswordToDevice implements MigrationStep
{
	@Override
	public void upgrade(User user)
	{
		Optional<String> vpnPassword = user.takeVpnPassword();
		vpnPassword.ifPresent(p -> setPassworOnDefaultDevice(user, p));
	}

	private void setPassworOnDefaultDevice(User user, String password)
	{
		UserDevice defaultDevice = getDefaultDevice(user);
		defaultDevice.setLegacyVpnAccountPassword(password);
	}

	private UserDevice getDefaultDevice(User user)
	{
		return user.getDevices().stream().sorted(
				(d1, d2) -> Integer.compare(d1.getDeviceAnonymized().getDeviceIndex(), d2.getDeviceAnonymized().getDeviceIndex()))
				.findFirst().orElseThrow(() -> DeviceServiceException.noDevicesFound(user.getAnonymized().getId()));
	}
}
