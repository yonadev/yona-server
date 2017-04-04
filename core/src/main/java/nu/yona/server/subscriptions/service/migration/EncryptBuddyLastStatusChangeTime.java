/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.MigratePrivateUserDataService;

public class EncryptBuddyLastStatusChangeTime extends MigratePrivateUserDataService.MigrationStep
{
	@SuppressWarnings("deprecation")
	@Override
	public void upgrade(User userEntity)
	{
		userEntity.getBuddies()
				.forEach(buddy -> buddy.setLastStatusChangeTime(buddy.getBuddyAnonymized().getLastStatusChangeTime()));
	}
}