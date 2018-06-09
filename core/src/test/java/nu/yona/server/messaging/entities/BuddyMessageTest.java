/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.repository.Repository;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.UserAnonymizedRepositoryMock;
import nu.yona.server.entities.UserRepositoryMock;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.PrivateUserProperties;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;

public class BuddyMessageTest
{
	@RunWith(MockitoJUnitRunner.class)
	public static class BuddyInfoParametersTest
	{
		private static final String FIRST_NAME_SUBSTITUTE = "FN BD";
		private static final String LAST_NAME_SUBSTITUTE = "LN BD";
		private static final String PASSWORD = "password";
		private final Field translatorStaticField = JUnitUtil.getAccessibleField(Translator.class, "staticReference");
		private final Field privateUserPropertiesFirstNameField = JUnitUtil.getAccessibleField(PrivateUserProperties.class,
				"firstName");
		private final Field privateUserPropertiesLastNameField = JUnitUtil.getAccessibleField(PrivateUserProperties.class,
				"lastName");

		@Rule
		public MethodRule cryptoSession = new CryptoSessionRule(PASSWORD);

		@Mock
		private Translator translator;

		private User richard;
		private User bob;

		@Before
		public void setUpPerTest() throws Exception
		{
			translatorStaticField.set(null, translator);
			Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
			repositoriesMap.put(User.class, new UserRepositoryMock());
			repositoriesMap.put(UserAnonymized.class, new UserAnonymizedRepositoryMock());
			repositoriesMap.put(BuddyAnonymized.class, new BuddyAnonymizedRepositoryMock());
			JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);

			try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
			{
				richard = JUnitUtil.createRichard();
				bob = JUnitUtil.createBob();
				JUnitUtil.makeBuddies(richard, bob);
			}

			Object[] params = { bob.getNickname() };
			when(translator.getLocalizedMessage("message.alternative.first.name", params)).thenReturn(FIRST_NAME_SUBSTITUTE);
			when(translator.getLocalizedMessage("message.alternative.last.name", params)).thenReturn(LAST_NAME_SUBSTITUTE);
		}

		@Test
		public void createInstance_buddyWithName_nameReturned()
		{
			Buddy buddy = richard.getBuddies().iterator().next();
			BuddyInfoParameters buddyInfoParameters = BuddyMessage.BuddyInfoParameters.createInstance(buddy,
					buddy.getBuddyAnonymizedId());

			assertThat(buddyInfoParameters.firstName, equalTo(bob.getFirstName()));
			assertThat(buddyInfoParameters.lastName, equalTo(bob.getLastName()));
		}

		@Test
		public void createInstance_buddyWithoutName_substituteReturned() throws IllegalArgumentException, IllegalAccessException
		{
			Buddy buddy = richard.getBuddies().iterator().next();
			privateUserPropertiesFirstNameField.set(buddy, null);
			privateUserPropertiesLastNameField.set(buddy, null);
			BuddyInfoParameters buddyInfoParameters = BuddyMessage.BuddyInfoParameters.createInstance(buddy,
					buddy.getBuddyAnonymizedId());

			assertThat(buddyInfoParameters.firstName, equalTo(FIRST_NAME_SUBSTITUTE));
			assertThat(buddyInfoParameters.lastName, equalTo(LAST_NAME_SUBSTITUTE));
		}
	}
}
