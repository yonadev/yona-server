/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.Translator;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.ActivityCategoryRepository;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@EnableCaching
@ComponentScan(value = "nu.yona.server.goals.service", resourcePattern = "**/ActivityCategoryService.class")
class ActivityCategoryServiceTestConfiguration
{
	static ConcurrentMapCache mockActivityCategorySetCache = new ConcurrentMapCache("activityCategorySet");

	@Bean
	public SimpleCacheManager cacheManager()
	{
		SimpleCacheManager cacheManager = new SimpleCacheManager();
		cacheManager.setCaches(Arrays.asList(mockActivityCategorySetCache));
		return cacheManager;
	}
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { ActivityCategoryServiceTestConfiguration.class })
public class ActivityCategoryServiceTest
{
	@MockBean
	private ActivityCategoryRepository mockRepository;

	private List<ActivityCategory> mockRepositoryFindAllResult;

	private ActivityCategory gambling;
	private ActivityCategory news;

	@Before
	public void setUpPerTest()
	{
		LocaleContextHolder.setLocale(Translator.EN_US_LOCALE);
		gambling = ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
				new HashSet<>(Arrays.asList("poker", "lotto")), Collections.emptySet(), usString("Descr"));
		news = ActivityCategory.createInstance(UUID.randomUUID(), usString("news"), false,
				new HashSet<>(Arrays.asList("refdag", "bbc")), Collections.emptySet(), usString("Descr"));

		mockRepositoryFindAllResult = new ArrayList<>();
		mockRepositoryFindAllResult.add(gambling);
		mockRepositoryFindAllResult.add(news);

		JUnitUtil.setUpRepositoryMock(mockRepository);
		when(mockRepository.findAll()).thenReturn(Collections.unmodifiableList(mockRepositoryFindAllResult));
		when(mockRepository.findOne(gambling.getId())).thenReturn(gambling);
		when(mockRepository.findOne(news.getId())).thenReturn(news);

		ActivityCategoryServiceTestConfiguration.mockActivityCategorySetCache.clear();
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	@Autowired
	private ActivityCategoryService service;

	@Test
	public void getAllActivityCategories_default_returnsDefinedActivityCategories()
	{
		assertGetAllActivityCategoriesResult("gambling", "news");
	}

	@Test
	public void getActivityCategory_existingId_returnsCorrectActivityCategory()
	{
		assertThat(service.getActivityCategory(gambling.getId()).getName(), equalTo("gambling"));
		assertThat(service.getActivityCategory(news.getId()).getName(), equalTo("news"));
	}

	@Test(expected = ActivityCategoryException.class)
	public void getActivityCategory_unknownId_throws()
	{
		service.getActivityCategory(UUID.randomUUID());
	}

	@Test
	public void updateActivityCategorySet_modifiedActivityCategories_setIsUpdatedCorrectly()
	{
		assertGetAllActivityCategoriesResult("gambling", "news");

		// modify
		Set<ActivityCategoryDto> importActivityCategories = new HashSet<>();
		ActivityCategoryDto newsModified = new ActivityCategoryDto(news.getId(), usString("news"), false,
				new HashSet<>(Arrays.asList("refdag", "bbc", "atom feeds")), new HashSet<String>(), usString("Descr"));
		importActivityCategories.add(newsModified);
		ActivityCategoryDto gaming = new ActivityCategoryDto(UUID.randomUUID(), usString("gaming"), false,
				new HashSet<>(Arrays.asList("games")), new HashSet<String>(), usString("Descr"));
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

	@Test
	public void getAllActivityCategories_default_returnsItemsFromCache()
	{
		assertGetAllActivityCategoriesResult("gambling", "news");

		ActivityCategory gaming = ActivityCategory.createInstance(UUID.randomUUID(), usString("gaming"), false,
				new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr"));
		// Add to collection, so it appears as if it is in the database
		// It hasn't been loaded, so should not be in the cache
		mockRepositoryFindAllResult.add(gaming);
		when(mockRepository.findOne(gaming.getId())).thenReturn(gaming);

		assertGetAllActivityCategoriesResult("gambling", "news");
	}

	@Test
	public void addActivityCategory_default_updatesCache()
	{
		ActivityCategory gaming = ActivityCategory.createInstance(UUID.randomUUID(), usString("gaming"), false,
				new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr"));
		when(mockRepository.findOne(gaming.getId())).thenReturn(gaming);

		service.addActivityCategory(ActivityCategoryDto.createInstance(gaming));

		mockRepositoryFindAllResult.add(gaming);
		assertGetAllActivityCategoriesResult("gambling", "news", "gaming");
	}

	@Test
	public void updateActivityCategory_default_updatesCache()
	{
		news.setLocalizableName(usString("news feeds"));

		service.updateActivityCategory(news.getId(), ActivityCategoryDto.createInstance(news));

		assertGetAllActivityCategoriesResult("gambling", "news feeds");
	}

	@Test
	public void deleteActivityCategory_default_updatesCache()
	{
		service.deleteActivityCategory(news.getId());

		mockRepositoryFindAllResult.remove(news);
		assertGetAllActivityCategoriesResult("gambling");
	}

	private void assertGetAllActivityCategoriesResult(String... names)
	{
		assertThat(service.getAllActivityCategories().stream().map(a -> a.getName()).collect(Collectors.toSet()),
				containsInAnyOrder(names));
	}
}
