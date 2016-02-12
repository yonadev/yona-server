package nu.yona.server.goals.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.ActivityCategoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class ActivityCategoryServiceTest
{
	private Set<ActivityCategory> activityCategories = new HashSet<ActivityCategory>();

	@Mock
	ActivityCategoryRepository mockRepository = mock(ActivityCategoryRepository.class);
	@InjectMocks
	private ActivityCategoryService service = new ActivityCategoryService();

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
	 * Tests import.
	 */
	@Test
	public void importActivityCategories()
	{
		assertGetAllActivityCategoriesResult("Initial", "gambling", "news");

		// modify
		Set<ActivityCategoryDTO> importActivityCategories = new HashSet<ActivityCategoryDTO>();
		ActivityCategoryDTO newsModified = new ActivityCategoryDTO("news", false,
				new HashSet<String>(Arrays.asList("refdag", "bbc", "atom feeds")), new HashSet<String>());
		importActivityCategories.add(newsModified);
		ActivityCategoryDTO gaming = new ActivityCategoryDTO("gaming", false, new HashSet<String>(Arrays.asList("games")),
				new HashSet<String>());
		importActivityCategories.add(gaming);

		service.importActivityCategories(importActivityCategories);

		ArgumentCaptor<ActivityCategory> matchActivityCategory = ArgumentCaptor.forClass(ActivityCategory.class);
		// 1 added and 1 updated
		verify(mockRepository, times(2)).save(matchActivityCategory.capture());
		assertThat(matchActivityCategory.getAllValues().stream().map(x -> x.getName()).collect(Collectors.toSet()),
				containsInAnyOrder("news", "gaming"));
		// 1 deleted
		verify(mockRepository, times(1)).delete(matchActivityCategory.capture());
		assertThat(matchActivityCategory.getValue().getName(), equalTo("gambling"));
	}
}
