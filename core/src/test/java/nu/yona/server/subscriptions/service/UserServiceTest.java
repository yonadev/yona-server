/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.subscriptions.service.UserService.UserPurpose;
import nu.yona.server.test.util.BaseSpringIntegrationTest;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.properties" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) })
class UserServiceTestConfiguration extends UserRepositoriesConfiguration
{
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { UserServiceTestConfiguration.class })
public class UserServiceTest extends BaseSpringIntegrationTest
{
	@MockBean
	private MessageSourceRepository mockMessageSourceRepository;

	@Autowired
	private UserService service;

	@Before
	public void setUpPerTest()
	{
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(MessageSource.class, mockMessageSourceRepository);
		return repositoriesMap;
	}

	@Test
	public void assertValidUserFields_buddyWithAllowedFields_doesNotThrow()
	{
		UserDto user = new UserDto("John", "Doe", "john@doe.net", "+31612345678", "jd");

		service.assertValidUserFields(user, UserPurpose.BUDDY);
	}
}