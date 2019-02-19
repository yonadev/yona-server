/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;

import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;

public class UserRepositoriesConfiguration
{
	@Bean
	UserRepository getMockUserRepository()
	{
		return Mockito.spy(new UserRepositoryMock());
	}

	@Bean
	UserAnonymizedRepository getMockUserAnonymizedRepository()
	{
		return Mockito.spy(new UserAnonymizedRepositoryMock());
	}
}
