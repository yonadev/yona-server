/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import nu.yona.server.Translator;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.ActivityCategoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class ActivityCategoryServiceTest extends ActivityCategoryServiceTestBase
{
	@Mock
	private final ActivityCategoryRepository mockRepository = mock(ActivityCategoryRepository.class);
	@InjectMocks
	private final ActivityCategoryService service = new ActivityCategoryService();

	@Before
	public void setUp()
	{
		setUp(mockRepository);
	}

	/*
	 * Tests the method to get all categories.
	 */
	@Test
	public void getAllActivityCategories()
	{
		assertGetAllActivityCategoriesResult("Get all", "gambling", "news");
	}

	private void assertGetAllActivityCategoriesResult(String reason, String... names)
	{
		assertThat(reason, service.getAllActivityCategories().stream().map(a -> a.getName()).collect(Collectors.toSet()),
				containsInAnyOrder(names));
	}

	/*
	 * Tests the method to get a category.
	 */
	@Test
	public void getActivityCategory()
	{
		assertThat(service.getActivityCategory(gambling.getID()).getName(), equalTo("gambling"));
		assertThat(service.getActivityCategory(news.getID()).getName(), equalTo("news"));
	}

	/*
	 * Tests import.
	 */
	@Test
	public void importActivityCategories()
	{
		assertGetAllActivityCategoriesResult("Initial", "gambling", "news");

		// modify
		Set<ActivityCategoryDTO> importActivityCategories = new HashSet<ActivityCategoryDTO>();
		ActivityCategoryDTO newsModified = new ActivityCategoryDTO(news.getID(), usString("news"), false,
				new HashSet<String>(Arrays.asList("refdag", "bbc", "atom feeds")), new HashSet<String>(), usString("Descr"));
		importActivityCategories.add(newsModified);
		ActivityCategoryDTO gaming = new ActivityCategoryDTO(UUID.randomUUID(), usString("gaming"), false,
				new HashSet<String>(Arrays.asList("games")), new HashSet<String>(), usString("Descr"));
		importActivityCategories.add(gaming);

		service.updateActivityCategorySet(importActivityCategories);

		ArgumentCaptor<ActivityCategory> matchActivityCategory = ArgumentCaptor.forClass(ActivityCategory.class);
		// 1 added and 1 updated
		verify(mockRepository, times(2)).save(matchActivityCategory.capture());
		assertThat(matchActivityCategory.getAllValues().stream().map(x -> x.getLocalizableName().get(Translator.EN_US_LOCALE))
				.collect(Collectors.toSet()), containsInAnyOrder("news", "gaming"));
		// 1 deleted
		verify(mockRepository, times(1)).delete(matchActivityCategory.capture());
		assertThat(matchActivityCategory.getValue().getLocalizableName().get(Translator.EN_US_LOCALE), equalTo("gambling"));
	}
}
