/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.repository.Repository;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.MessageSourceRepositoryMock;
import nu.yona.server.entities.UserAnonymizedRepositoryMock;
import nu.yona.server.entities.UserRepositoryMock;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.PrivateUserProperties;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.test.util.InCryptoSession;
import nu.yona.server.test.util.JUnitUtil;

public class BuddyMessageTest
{
	private static final String PASSWORD = "password";

	@ExtendWith(MockitoExtension.class)
	@InCryptoSession(PASSWORD)
	static class BuddyInfoParametersTest
	{
		private static final String FIRST_NAME_SUBSTITUTE = "FN BD";
		private static final String LAST_NAME_SUBSTITUTE = "LN BD";
		private final Field translatorStaticField = JUnitUtil.getAccessibleField(Translator.class, "staticReference");
		private final Field privateUserPropertiesFirstNameField = JUnitUtil.getAccessibleField(PrivateUserProperties.class,
				"firstName");
		private final Field privateUserPropertiesLastNameField = JUnitUtil.getAccessibleField(PrivateUserProperties.class,
				"lastName");

		@Mock
		private Translator translator;

		private User richard;
		private User bob;

		@BeforeEach
		public void setUpPerTest() throws Exception
		{
			translatorStaticField.set(null, translator);
			Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
			repositoriesMap.put(User.class, new UserRepositoryMock());
			repositoriesMap.put(UserAnonymized.class, new UserAnonymizedRepositoryMock());
			repositoriesMap.put(BuddyAnonymized.class, new BuddyAnonymizedRepositoryMock());
			repositoriesMap.put(MessageSource.class, new MessageSourceRepositoryMock());
			JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);

			try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
			{
				richard = JUnitUtil.createRichard();
				bob = JUnitUtil.createBob();
				JUnitUtil.makeBuddies(richard, bob);
			}

			Object[] params = { bob.getNickname() };
			lenient().when(translator.getLocalizedMessage("message.alternative.first.name", params))
					.thenReturn(FIRST_NAME_SUBSTITUTE);
			lenient().when(translator.getLocalizedMessage("message.alternative.last.name", params))
					.thenReturn(LAST_NAME_SUBSTITUTE);
		}

		@Test
		void createInstance_buddyWithName_nameReturned()
		{
			Buddy buddy = richard.getBuddies().iterator().next();
			BuddyInfoParameters buddyInfoParameters = BuddyMessage.BuddyInfoParameters.createInstance(buddy, Optional.empty());

			assertThat(buddyInfoParameters.firstName, equalTo(bob.getFirstName()));
			assertThat(buddyInfoParameters.lastName, equalTo(bob.getLastName()));
		}

		@Test
		void createInstance_buddyWithoutName_substituteReturned() throws IllegalArgumentException, IllegalAccessException
		{
			Buddy buddy = richard.getBuddies().iterator().next();
			privateUserPropertiesFirstNameField.set(buddy, null);
			privateUserPropertiesLastNameField.set(buddy, null);
			BuddyInfoParameters buddyInfoParameters = BuddyMessage.BuddyInfoParameters.createInstance(buddy, Optional.empty());

			assertThat(buddyInfoParameters.firstName, equalTo(FIRST_NAME_SUBSTITUTE));
			assertThat(buddyInfoParameters.lastName, equalTo(LAST_NAME_SUBSTITUTE));
		}
	}
}
