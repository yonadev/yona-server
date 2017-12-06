/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test.util;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.support.Repositories;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.messaging.entities.MessageSource;
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
		when(mockRepository.save(Matchers.<T> any())).thenAnswer(new Answer<T>() {
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
				return repository;
			}
		});
		RepositoryProvider.setRepositories(mockRepositories);
		return mockRepositories;
	}

	public static User createUserEntity()
	{
		return createUserEntity("John", "Doe", "+31612345678", "jd", "topSecret");
	}

	public static User createUserEntity(String firstName, String lastName, String mobileNumber, String nickname,
			String vpnPassword)
	{
		MessageSource anonymousMessageSource = MessageSource.createInstance();
		UserAnonymized johnAnonymized = UserAnonymized.createInstance(anonymousMessageSource.getDestination(),
				Collections.emptySet());
		UserAnonymized.getRepository().save(johnAnonymized);

		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource namedMessageSource = MessageSource.createInstance();
		UserPrivate userPrivate = UserPrivate.createInstance(nickname, vpnPassword, johnAnonymized.getId(),
				anonymousMessageSource.getId(), namedMessageSource);
		return new User(UUID.randomUUID(), initializationVector, firstName, lastName, mobileNumber, userPrivate,
				namedMessageSource.getDestination());

	}
}
