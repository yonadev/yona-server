/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.mockito.MockingDetails;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.Translator;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;

@RunWith(SpringJUnit4ClassRunner.class)
public abstract class BaseSpringIntegrationTest
{
	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected UserAnonymizedRepository userAnonymizedRepository;

	@BeforeClass
	public static void setUpForAll()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
	}

	@Before
	public final void setUpPerTestBase()
	{
		MockitoAnnotations.initMocks(this);

		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(User.class, userRepository);
		repositoriesMap.put(UserAnonymized.class, userAnonymizedRepository);
		repositoriesMap.putAll(getRepositories());
		Set<CrudRepository<?, ?>> crudRepositories = filterForCrudRepositories(repositoriesMap.values());
		crudRepositories.forEach(CrudRepository::deleteAll);
		crudRepositories.stream().filter(this::isMock).forEach(r -> JUnitUtil.setUpRepositoryMock(r));
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);
	}

	private Set<CrudRepository<?, ?>> filterForCrudRepositories(Collection<Repository<?, ?>> values)
	{
		return values.stream().filter(r -> r instanceof CrudRepository).map(r -> (CrudRepository<?, ?>) r)
				.collect(Collectors.toSet());
	}

	private boolean isMock(Object objectoInspect)
	{
		MockingDetails mockingDetails = Mockito.mockingDetails(objectoInspect);
		return mockingDetails.isMock() && !mockingDetails.isSpy();
	}

	protected abstract Map<Class<?>, Repository<?, ?>> getRepositories();
}
