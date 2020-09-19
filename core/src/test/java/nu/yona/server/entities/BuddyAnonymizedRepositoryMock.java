/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;

public class BuddyAnonymizedRepositoryMock extends MockJpaRepositoryEntityWithUuid<BuddyAnonymized>
		implements BuddyAnonymizedRepository
{
	@Override
	public boolean existsByOwningUserAnonymizedIdAndUserAnonymizedId(UUID owningUserAnonymized, UUID userAnonymizedId)
	{
		throw new NotImplementedException();
	}
}