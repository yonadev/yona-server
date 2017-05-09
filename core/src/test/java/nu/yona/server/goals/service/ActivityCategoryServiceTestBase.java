/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;

import nu.yona.server.Translator;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.ActivityCategoryRepository;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@EnableCaching
@ComponentScan(value = "nu.yona.server.goals.service", resourcePattern = "**/ActivityCategoryService.class")
class ActivityCategoryServiceTestBaseConfiguration
{
	@Bean
	public SimpleCacheManager cacheManager()
	{
		SimpleCacheManager cacheManager = new SimpleCacheManager();
		cacheManager.setCaches(Arrays.asList(new ConcurrentMapCache("activityCategorySet")));
		return cacheManager;
	}
}

public abstract class ActivityCategoryServiceTestBase
{
	@MockBean
	private ActivityCategoryRepository mockRepository;

	protected List<ActivityCategory> activityCategories = new ArrayList<>();

	protected ActivityCategory gambling;
	protected ActivityCategory news;

	@Before
	public void setUpPerTestBase()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
		gambling = ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
				new HashSet<>(Arrays.asList("poker", "lotto")), Collections.emptySet(), usString("Descr"));
		news = ActivityCategory.createInstance(UUID.randomUUID(), usString("news"), false,
				new HashSet<>(Arrays.asList("refdag", "bbc")), Collections.emptySet(), usString("Descr"));

		activityCategories.add(gambling);
		activityCategories.add(news);

		JUnitUtil.setUpRepositoryMock(getMockRepository());
		when(getMockRepository().findAll()).thenReturn(Collections.unmodifiableList(activityCategories));
		when(getMockRepository().findOne(gambling.getId())).thenReturn(gambling);
		when(getMockRepository().findOne(news.getId())).thenReturn(news);
	}

	protected ActivityCategoryRepository getMockRepository()
	{
		return mockRepository;
	}

	protected Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}
}
