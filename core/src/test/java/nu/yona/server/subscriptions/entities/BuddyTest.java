/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;

@RunWith(MockitoJUnitRunner.class)
public class BuddyTest
{
	private static final String PASSWORD = "PASSWORD";

	private final Field translatorStaticField = JUnitUtil.getAccessibleField(Translator.class, "staticReference");
	private final Field userUserPrivateField = JUnitUtil.getAccessibleField(User.class, "userPrivate");
	private final Field privateUserPropertiesFirstNameField = JUnitUtil.getAccessibleField(PrivateUserProperties.class,
			"firstName");

	@Mock
	private Translator translator;

	@Mock
	private MessageSource messageSource;

	@Mock
	private MessageDestination messageDestination;

	@Rule
	public MethodRule cryptoSession = new CryptoSessionRule(PASSWORD);

	@Before
	public void setUpPerTest() throws Exception
	{
		translatorStaticField.set(null, translator);

		when(messageSource.getId()).thenReturn(UUID.randomUUID());
		when(translator.getLocalizedMessage(any(String.class), anyVararg())).thenAnswer(new Answer<String>() {
			@Override
			public String answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				assert args.length == 2;
				String messageId = (String) args[0];
				String insertion = (String) args[1];
				return messageId + ":" + insertion;
			}
		});

	}

	@Test
	public void determineName_buddyNameAvailable_buddyName()
	{
		String buddyName = "BuddyName";

		String name = Buddy.determineName(() -> buddyName, null, null, null, null);

		assertThat(name, equalTo(buddyName));
	}

	@Test
	public void determineName_nameNotAvailableInUser_translationUsed() throws IllegalArgumentException, IllegalAccessException
	{
		String nickname = "nickname";
		String messageId = "message.id";
		User user = createUser("firstName", "lastName", nickname);
		privateUserPropertiesFirstNameField.set(userUserPrivateField.get(user), null);

		String name = Buddy.determineName(() -> null, Optional.of(user), User::getFirstName, messageId, nickname);

		assertThat(name, equalTo(messageId + ":" + nickname));
	}

	@Test
	public void determineName_userTooNewToContainName_translationUsed()
	{
		String nickname = "nickname";
		String messageId = "message.id";
		User user = createUser("firstName", "lastName", nickname);
		user.setPrivateDataMigrationVersion(9999);

		String name = Buddy.determineName(() -> null, Optional.of(user), User::getFirstName, messageId, nickname);

		assertThat(name, equalTo(messageId + ":" + nickname));
	}

	@Test
	public void determineName_firstNameAvailable_firstName()
	{
		String userName = "firstName";
		User user = createUser(userName, "lastName", "nickName");
		user.setPrivateDataMigrationVersion(0);

		String name = Buddy.determineName(() -> null, Optional.of(user), User::getFirstName, null, null);

		assertThat(name, equalTo(userName));
	}

	@Test
	public void determineName_userNotAvailable_translationUsed()
	{
		String nickname = "nickname";
		String messageId = "message.id";

		String name = Buddy.determineName(() -> null, Optional.empty(), User::getFirstName, messageId, nickname);

		assertThat(name, equalTo(messageId + ":" + nickname));
	}

	private User createUser(String firstName, String lastName, String nickname)
	{
		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		UserPrivate userPrivate = UserPrivate.createInstance(firstName, lastName, nickname, UUID.randomUUID(), UUID.randomUUID(),
				messageSource);
		return new User(UUID.randomUUID(), initializationVector, "+31123456", userPrivate, messageDestination);
	}
}