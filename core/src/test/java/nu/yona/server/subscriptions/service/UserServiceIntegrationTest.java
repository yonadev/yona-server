/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.repository.support.Repositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.subscriptions.service.UserServiceIntegrationTest.TestConfiguration;
import nu.yona.server.util.TimeUtil;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TestConfiguration.class })
public class UserServiceIntegrationTest
{
	@Autowired
	private UserRepository mockUserRepository;

	@Autowired
	private MessageSourceRepository mockMessageSourceRepository;

	@Autowired
	private UserAnonymizedRepository mockUserAnonymizedRepository;

	@Autowired
	private UserService service;

	private Repositories mockRepositories;

	private final String password = "password";

	private User john;

	@Before
	public void setUpForAll()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@Before
	public void setUpPerTest()
	{
		// The repositories are injected beans that are reused per test. Reset them.
		reset(mockUserRepository);
		reset(mockMessageSourceRepository);
		reset(mockUserAnonymizedRepository);

		mockRepositories = Mockito.mock(Repositories.class);

		// save should not return null but the saved entity
		when(mockUserRepository.save(any(User.class))).thenAnswer(new Answer<User>() {
			@Override
			public User answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (User) args[0];
			}
		});

		// save should not return null but the saved entity
		when(mockMessageSourceRepository.save(any(MessageSource.class))).thenAnswer(new Answer<MessageSource>() {
			@Override
			public MessageSource answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (MessageSource) args[0];
			}
		});

		// save should not return null but the saved entity
		when(mockUserAnonymizedRepository.save(any(UserAnonymized.class))).thenAnswer(new Answer<UserAnonymized>() {
			@Override
			public UserAnonymized answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (UserAnonymized) args[0];
			}
		});

		when(mockRepositories.getRepositoryFor(any())).thenAnswer(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable
			{
				Class<?> entityClass = (Class<?>) invocation.getArguments()[0];
				if (entityClass == MessageSource.class)
				{
					return mockMessageSourceRepository;
				}
				if (entityClass == UserAnonymized.class)
				{
					return mockUserAnonymizedRepository;
				}
				throw new IllegalArgumentException("Unsupported class: " + entityClass.getName());
			}
		});

		RepositoryProvider.setRepositories(mockRepositories);

		try (CryptoSession cryptoSession = CryptoSession.start(password))
		{
			john = User.createInstance("John", "Doe", "jd", "+31612345678", "topSecret", Collections.emptySet());
		}

		when(mockUserRepository.findOne(john.getId())).thenReturn(john);
	}

	/*
	 * Test that the "last app opened date" is updated when the app was opened yesterday
	 */
	@Test
	public void postOpenAppEventNextDay()
	{
		john.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate().minusDays(1));
		service.postOpenAppEvent(john.getId());

		assertThat(john.getAppLastOpenedDate(), equalTo(TimeUtil.utcNow().toLocalDate()));

		verify(mockUserRepository, times(1)).save(any(User.class));
	}

	/*
	 * Test that the "last app opened date" is NOT updated twice on the same day
	 */
	@Test
	public void postOpenAppEventSameDay()
	{
		john.setAppLastOpenedDate(TimeUtil.utcNow().toLocalDate());
		service.postOpenAppEvent(john.getId());

		verify(mockUserRepository, times(0)).save(any(User.class));
	}

	@Configuration
	@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
			"nu.yona.server.properties" }, includeFilters = {
					@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserService", type = FilterType.REGEX),
					@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) })
	static class TestConfiguration
	{
		@Bean
		UserRepository mockUserRepository()
		{
			return Mockito.mock(UserRepository.class);
		}

		@Bean
		MessageSourceRepository mockMessageSourceRepository()
		{
			return Mockito.mock(MessageSourceRepository.class);
		}

		@Bean
		UserAnonymizedRepository mockUserAnonymizedRepository()
		{
			return Mockito.mock(UserAnonymizedRepository.class);
		}
	}
}
