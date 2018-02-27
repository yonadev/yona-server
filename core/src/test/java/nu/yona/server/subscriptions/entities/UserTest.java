/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.messaging.entities.MessageSource;

public class UserTest
{
	private final String password = "password";
	private User john;

	@Before
	public void setUpForAll()
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password))
		{
			byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
			MessageSource namedMessageSource = MessageSource.createInstance();
			UserPrivate userPrivate = UserPrivate.createInstance("jd", null, null, namedMessageSource);
			john = new User(UUID.randomUUID(), initializationVector, "John", "Doe", "+31612345678", userPrivate,
					namedMessageSource.getDestination());
		}
	}

	@Test
	public void getPrivateDataMigrationVersion_newUser_returnsCurrentVersion()
	{
		assertThat(john.getPrivateDataMigrationVersion(), equalTo(User.getCurrentPrivateDataMigrationVersion()));
	}
}