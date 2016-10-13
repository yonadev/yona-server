/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.ActivityCategoryRepository;
import nu.yona.server.goals.service.ActivityCategoryServiceIntegrationTest.TestConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TestConfiguration.class })
public class ActivityCategoryServiceIntegrationTest extends ActivityCategoryServiceTestBase
{
	@Autowired
	private ActivityCategoryRepository mockRepository;

	@Autowired
	private ActivityCategoryService service;

	@Before
	public void setUp()
	{
		setUp(mockRepository);
	}

	private void assertGetAllActivityCategoriesResult(String reason, String... names)
	{
		assertThat(reason, service.getAllActivityCategories().stream().map(a -> a.getName()).collect(Collectors.toSet()),
				containsInAnyOrder(names));
	}

	/*
	 * Tests if the cache is expired after update or delete or add.
	 */
	@Test
	public void caching()
	{
		assertGetAllActivityCategoriesResult("Initial", "gambling", "news");

		ActivityCategory dummy = ActivityCategory.createInstance(UUID.randomUUID(), usString("dummy"), false,
				new HashSet<String>(Arrays.asList("games")), Collections.emptySet(), usString("Descr"));
		ActivityCategory gaming = ActivityCategory.createInstance(UUID.randomUUID(), usString("gaming"), false,
				new HashSet<String>(Arrays.asList("games")), Collections.emptySet(), usString("Descr"));
		// Add to collection, so it appears as if it is in the database
		// It hasn't been loaded, so should not be in the cache
		activityCategories.add(gaming);
		when(mockRepository.findOne(gaming.getID())).thenReturn(gaming);

		assertGetAllActivityCategoriesResult("Set expected to be cached", "gambling", "news");

		// Add dummy activity category to trigger cache eviction
		service.addActivityCategory(ActivityCategoryDTO.createInstance(dummy));

		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after add", "gambling", "news", "gaming");

		gaming.setLocalizableName(usString("amusement"));
		service.updateActivityCategory(gaming.getID(), ActivityCategoryDTO.createInstance(gaming));

		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after add", "gambling", "news", "amusement");
		activityCategories.remove(news);
		service.deleteActivityCategory(news.getID());

		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after add", "gambling", "amusement");

		activityCategories.add(news);
		activityCategories.remove(gaming);
		service.importActivityCategories(
				activityCategories.stream().map(a -> ActivityCategoryDTO.createInstance(a)).collect(Collectors.toSet()));
		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after import", "gambling", "news");
	}

	@Configuration
	@EnableCaching
	@ComponentScan(value = "nu.yona.server.goals.service", resourcePattern = "**/ActivityCategoryService.class")
	static class TestConfiguration
	{
		@Bean
		public SimpleCacheManager cacheManager()
		{
			SimpleCacheManager cacheManager = new SimpleCacheManager();
			cacheManager.setCaches(Arrays.asList(new ConcurrentMapCache("activityCategorySet")));
			return cacheManager;
		}

		@Bean
		ActivityCategoryRepository mockRepository()
		{
			return Mockito.mock(ActivityCategoryRepository.class);
		}
	}
}
