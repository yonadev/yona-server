/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.subscriptions.service.UserDto;

@Component
@Order(3) // Never change the order or remove a migration step. If the step is obsolete, make it empty
public class EncryptFirstAndLastName implements MigrationStep
{
	@Autowired
	private BuddyService buddyService;

	@Override
	public void upgrade(User user)
	{
		UserDto originalUser = UserDto.createInstanceWithPrivateData(user, Collections.emptySet());
		user.moveFirstAndLastNameToPrivate();
		buddyService.broadcastUserInfoChangeToBuddies(user, originalUser);
	}
}
