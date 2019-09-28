/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test.util;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.support.Repositories;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserPrivate;

public class JUnitUtil
{

	private JUnitUtil()
	{
		// No instances
	}

	@SuppressWarnings("unchecked")
	public static <T, ID extends Serializable> void setUpRepositoryMock(CrudRepository<T, ID> mockRepository)
	{
		// save should not return null but the saved entity
		Mockito.lenient().when(mockRepository.save(ArgumentMatchers.<T> any())).thenAnswer(new Answer<T>() {
			@Override
			public T answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (T) args[0];
			}
		});

	}

	public static Repositories setUpRepositoryProviderMock(Map<Class<?>, Repository<?, ?>> repositoriesMap)
	{
		Repositories mockRepositories = Mockito.mock(Repositories.class);
		when(mockRepositories.getRepositoryFor(any())).thenAnswer(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable
			{
				Class<?> entityClass = (Class<?>) invocation.getArguments()[0];
				Repository<?, ?> repository = repositoriesMap.get(entityClass);
				if (repository == null)
				{
					throw new IllegalArgumentException("Unsupported class: " + entityClass.getName());
				}
				return Optional.of(repository);
			}
		});
		RepositoryProvider.setRepositories(mockRepositories);
		return mockRepositories;
	}

	public static User createRichard()
	{
		return createUserEntity("Richard", "Quinn", "+31610000000", "RQ");
	}

	public static User createBob()
	{
		return createUserEntity("Bob", "Dunn", "+31610000001", "BD");
	}

	public static User createUserEntity(String firstName, String lastName, String mobileNumber, String nickname)
	{
		MessageSource anonymousMessageSource = MessageSource.createInstance();
		UserAnonymized johnAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(),
				Collections.emptySet());
		UserAnonymized.getRepository().save(johnAnonymized);

		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource namedMessageSource = MessageSource.createInstance();
		MessageSource.getRepository().save(namedMessageSource);
		UserPrivate userPrivate = UserPrivate.createInstance(firstName, lastName, nickname, johnAnonymized.getId(),
				anonymousMessageSource.getId(), namedMessageSource);
		User user = new User(UUID.randomUUID(), initializationVector, mobileNumber, userPrivate,
				namedMessageSource.getDestination());
		return User.getRepository().save(user);
	}

	public static void makeBuddies(User user, User buddyToBe)
	{
		addAsBuddy(user, buddyToBe);
		addAsBuddy(buddyToBe, user);
	}

	private static void addAsBuddy(User user, User buddyToBe)
	{
		Buddy buddy = Buddy.createInstance(buddyToBe.getId(), buddyToBe.getFirstName(), buddyToBe.getLastName(),
				buddyToBe.getNickname(), Optional.empty(), Status.ACCEPTED, Status.ACCEPTED);
		buddy.setUserAnonymizedId(buddyToBe.getAnonymized().getId());
		user.addBuddy(buddy);
	}

	public static Field getAccessibleField(Class<?> clazz, String fieldName)
	{
		Field field = Arrays.asList(clazz.getDeclaredFields()).stream().filter(f -> f.getName().equals(fieldName)).findAny()
				.orElseThrow(() -> new IllegalStateException("Cannot find field '" + fieldName + "'"));
		field.setAccessible(true);
		return field;
	}

	public static void skipBefore(String description, ZonedDateTime now, int hour, int minute)
	{
		assumeTrue(now.isAfter(now.withHour(hour).withMinute(minute).withSecond(0)), description);
	}
}