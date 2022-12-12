/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.Order;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalRepository;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.HibernateHelperService;
import nu.yona.server.util.LockPool;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.properties" }, includeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.User.*Service", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.PrivateUserDataMigrationService", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.MockMigrationStep1", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.MockMigrationStep2", type = FilterType.REGEX) }, excludeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserPhotoService", type = FilterType.REGEX) })
class PrivateUserDataMigrationServiceIntegrationTestConfiguration extends UserRepositoriesConfiguration
{
}

@Component
@Order(0)
class MockMigrationStep1 implements MigrationStep
{
	@Override
	public void upgrade(User userEntity)
	{
		userEntity.setFirstName(userEntity.getFirstName() + "foo");
	}
}

@Component
@Order(1)
class MockMigrationStep2 implements MigrationStep
{
	@Override
	public void upgrade(User userEntity)
	{
		userEntity.setFirstName(userEntity.getFirstName() + "bar");
	}
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { PrivateUserDataMigrationServiceIntegrationTestConfiguration.class })
class PrivateUserDataMigrationServiceIntegrationTest extends BaseSpringIntegrationTest
{
	@MockBean
	private BuddyService buddyService;

	@MockBean
	private MessageService messageService;

	@MockBean
	private GoalRepository mockGoalRepository;

	@MockBean
	private MessageSourceRepository mockMessageSourceRepository;

	@MockBean
	private UserAnonymizedService mockUserAnonymizedService;

	@MockBean
	private LockPool<UUID> mockUserSynchronizer;

	@Autowired
	private PrivateUserDataMigrationService service;

	@MockBean
	private HibernateHelperService hibernateHelperService;

	@Autowired
	private UserService userService;

	private final static String PASSWORD = "password";
	private User richard;

	@BeforeEach
	public void setUpPerTest()
	{
		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			richard = JUnitUtil.createRichard();
		}
		reset(userRepository);
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(MessageSource.class, mockMessageSourceRepository);
		repositoriesMap.put(Goal.class, mockGoalRepository);
		return repositoriesMap;
	}

	@Test
	void doPreparationsAndCheckCanAccessPrivateData_twoVersionsBehind_migratesToCurrentVersion()
	{
		richard.setPrivateDataMigrationVersion(0);

		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			userService.doPreparationsAndCheckCanAccessPrivateData(richard.getId());

			ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
			verify(userRepository, times(1)).save(userCaptor.capture());
			assertThat(userCaptor.getValue().getFirstName(), equalTo("Richardfoobar"));
			assertThat(userCaptor.getValue().getPrivateDataMigrationVersion(), equalTo(service.getCurrentVersion()));
		}
	}

	@Test
	void doPreparationsAndCheckCanAccessPrivateData_upToDate_saveNotCalled()
	{
		richard.setPrivateDataMigrationVersion(2);

		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			userService.doPreparationsAndCheckCanAccessPrivateData(richard.getId());

			verify(userRepository, never()).save(any(User.class));
		}
	}
}
