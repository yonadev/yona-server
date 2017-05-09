/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.goals.entities.ActivityCategory;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { ActivityCategoryServiceTestBaseConfiguration.class })
public class ActivityCategoryServiceIntegrationTest extends ActivityCategoryServiceTestBase
{
	@Autowired
	private ActivityCategoryService service;

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
				new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr"));
		ActivityCategory gaming = ActivityCategory.createInstance(UUID.randomUUID(), usString("gaming"), false,
				new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr"));
		// Add to collection, so it appears as if it is in the database
		// It hasn't been loaded, so should not be in the cache
		activityCategories.add(gaming);
		when(getMockRepository().findOne(gaming.getId())).thenReturn(gaming);

		assertGetAllActivityCategoriesResult("Set expected to be cached", "gambling", "news");

		// Add dummy activity category to trigger cache eviction
		service.addActivityCategory(ActivityCategoryDto.createInstance(dummy));

		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after add", "gambling", "news", "gaming");

		gaming.setLocalizableName(usString("amusement"));
		service.updateActivityCategory(gaming.getId(), ActivityCategoryDto.createInstance(gaming));

		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after add", "gambling", "news", "amusement");
		activityCategories.remove(news);
		service.deleteActivityCategory(news.getId());

		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after add", "gambling", "amusement");

		activityCategories.add(news);
		activityCategories.remove(gaming);
		service.updateActivityCategorySet(
				activityCategories.stream().map(a -> ActivityCategoryDto.createInstance(a)).collect(Collectors.toSet()));
		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after import", "gambling", "news");
	}
}
