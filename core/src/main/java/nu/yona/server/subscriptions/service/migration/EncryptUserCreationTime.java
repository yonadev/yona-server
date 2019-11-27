/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;

@Component
@Order(4) // Never change the order or remove a migration step. If the step is obsolete, make it empty
public class EncryptUserCreationTime implements MigrationStep
{
	@Override
	public void upgrade(User user)
	{
		user.moveCreationTimeToPrivate();
	}
}
