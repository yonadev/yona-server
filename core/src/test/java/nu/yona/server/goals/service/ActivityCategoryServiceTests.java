package nu.yona.server.goals.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
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
import nu.yona.server.goals.service.ActivityCategoryServiceTests.TestConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TestConfiguration.class })
public class ActivityCategoryServiceTests
{
	private Set<ActivityCategory> activityCategories = new HashSet<ActivityCategory>();

	@Autowired
	ActivityCategoryRepository mockRepository;

	@Autowired
	private ActivityCategoryService service;

	private ActivityCategory gambling;
	private ActivityCategory news;

	@Before
	public void setUp()
	{
		gambling = ActivityCategory.createInstance("gambling", false, new HashSet<String>(Arrays.asList("poker", "lotto")),
				Collections.emptySet());
		news = ActivityCategory.createInstance("news", false, new HashSet<String>(Arrays.asList("refdag", "bbc")),
				Collections.emptySet());

		activityCategories.add(gambling);
		activityCategories.add(news);

		when(mockRepository.findAll()).thenReturn(Collections.unmodifiableSet(activityCategories));
		when(mockRepository.findOne(gambling.getID())).thenReturn(gambling);
		when(mockRepository.findOne(news.getID())).thenReturn(news);
		when(mockRepository.save(any(ActivityCategory.class))).thenAnswer(new Answer<ActivityCategory>() {
			@Override
			public ActivityCategory answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (ActivityCategory) args[0];
			}
		});
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
	 * Tests if the cache is expired after update or delete or add.
	 */
	@Test
	public void caching()
	{
		assertGetAllActivityCategoriesResult("Initial", "gambling", "news");

		ActivityCategory gaming = ActivityCategory.createInstance("gaming", false, new HashSet<String>(Arrays.asList("games")),
				Collections.emptySet());
		activityCategories.add(gaming);
		when(mockRepository.findOne(gaming.getID())).thenReturn(gaming);

		assertGetAllActivityCategoriesResult("Set expected to be cached", "gambling", "news");

		service.addActivityCategory(ActivityCategoryDTO.createInstance(gaming));

		assertGetAllActivityCategoriesResult("Cached set expected to be evicted after add", "gambling", "news", "gaming");

		gaming.setName("amusement");
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
	public static class TestConfiguration
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
