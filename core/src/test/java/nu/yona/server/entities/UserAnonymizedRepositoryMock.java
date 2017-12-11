/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.time.LocalDate;

import org.apache.commons.lang.NotImplementedException;

import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;

public class UserAnonymizedRepositoryMock extends MockJpaRepositoryEntityWithUuid<UserAnonymized>
		implements UserAnonymizedRepository
{

	@Override
	public int countByLastMonitoredActivityDateBetween(LocalDate startDate, LocalDate endDate)
	{
		throw new NotImplementedException();
	}

	@Override
	public int countByLastMonitoredActivityDateIsNull()
	{
		throw new NotImplementedException();
	}
}