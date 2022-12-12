/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.repository.Repository;

import nu.yona.server.Translator;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.service.DeviceAnonymizedDto;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.GoalRepository;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.TimeUtil;

public abstract class AnalysisEngineService_determineRelevantGoalsTest
{
	@Mock
	private GoalRepository mockGoalRepository;

	private Goal gamblingGoal;
	private Goal newsGoal;
	private Goal gamingGoal;
	private Goal socialGoal;
	private Goal shoppingGoal;
	private Goal shoppingGoalHistoryItem;
	private DeviceAnonymized deviceAnonEntity;
	private UserAnonymized userAnonEntity;
	private UserAnonymizedDto userAnonDto;

	private ZoneId userAnonZoneId;

	private DeviceAnonymizedDto deviceAnonDto;

	protected abstract OperatingSystem getOperatingSystem();

	@BeforeEach
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);

		setUpRepositoryMocks();

		LocalDateTime yesterday = TimeUtil.utcNow().minusDays(1);
		LocalDateTime dayBeforeYesterday = yesterday.minusDays(1);
		gamblingGoal = BudgetGoal.createNoGoInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
						new HashSet<>(Arrays.asList("poker", "lotto")), new HashSet<>(Arrays.asList("Poker App", "Lotto App")),
						usString("Descr")));
		newsGoal = BudgetGoal.createNoGoInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("news"), false,
						new HashSet<>(Arrays.asList("refdag", "bbc")), Collections.emptySet(), usString("Descr")));
		gamingGoal = BudgetGoal.createNoGoInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gaming"), false,
						new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr")));
		socialGoal = TimeZoneGoal.createInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("social"), false,
						new HashSet<>(Arrays.asList("social")), Collections.emptySet(), usString("Descr")),
				Collections.emptyList());
		shoppingGoal = BudgetGoal.createInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("shopping"), false,
						new HashSet<>(Arrays.asList("webshop")), Collections.emptySet(), usString("Descr")), 1);
		shoppingGoalHistoryItem = shoppingGoal.cloneAsHistoryItem(yesterday);

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination.createInstance(
				PublicKeyUtil.generateKeyPair().getPublic());
		Set<Goal> goals = new HashSet<>(
				Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal, shoppingGoalHistoryItem));
		deviceAnonEntity = DeviceAnonymized.createInstance(0, getOperatingSystem(), "Unknown", 0, Optional.empty(),
				Translator.EN_US_LOCALE);
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity, goals);
		userAnonEntity.addDeviceAnonymized(deviceAnonEntity);
		userAnonDto = UserAnonymizedDto.createInstance(userAnonEntity);
		deviceAnonDto = DeviceAnonymizedDto.createInstance(deviceAnonEntity);
		userAnonZoneId = userAnonDto.getTimeZone();
	}

	private void setUpRepositoryMocks()
	{
		JUnitUtil.setUpRepositoryMock(mockGoalRepository);
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(Goal.class, mockGoalRepository);
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);
	}

	protected Goal getSocialGoal()
	{
		return socialGoal;
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	@Test
	public void determineRelevantGoals_appActivityNotAUserGoal_emptySet()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(makeCategorySet(newsGoal)));
		assertThat(relevantGoals, empty());
	}

	@Test
	public void determineRelevantGoals_appActivityOneUserGoal_userGoal()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(makeCategorySet(socialGoal)));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(socialGoal)));
	}

	@Test
	public void determineRelevantGoals_appActivityTwoUserGoals_bothUserGoals()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(
				makeAppPayload(makeCategorySet(socialGoal, gamblingGoal)));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(socialGoal), GoalDto.createInstance(gamblingGoal)));
	}

	@Test
	public void determineRelevantGoals_appActivityTwoGoalsOneUserGoal_userGoal()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(
				makeAppPayload(makeCategorySet(newsGoal, gamblingGoal)));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(gamblingGoal)));
	}

	@Test
	public void determineRelevantGoals_appActivityBeforeGoalStart_emptySet()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(
				makeAppPayload(makeCategorySet(socialGoal), Duration.ofDays(3)));
		assertThat(relevantGoals, empty());
	}

	@Test
	public void determineRelevantGoals_appActivityOnGoalWithHistory_oneGoalReturned()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(
				makeAppPayload(makeCategorySet(shoppingGoal), Duration.ofDays(1)));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(shoppingGoal)));
	}

	@Test
	public void determineRelevantGoals_networkActivityOneNoGoUserGoal_userGoal()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(
				makeNetworkPayload(makeCategorySet(gamingGoal)));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(gamingGoal)));
	}

	protected Set<ActivityCategoryDto> makeCategorySet(Goal... goals)
	{
		return Arrays.asList(goals).stream().map(Goal::getActivityCategory).map(ActivityCategoryDto::createInstance)
				.collect(Collectors.toSet());
	}

	private ActivityPayload makeAppPayload(Set<ActivityCategoryDto> activityCategories)
	{
		return makeAppPayload(activityCategories, Duration.ofDays(0));
	}

	private ActivityPayload makeAppPayload(Set<ActivityCategoryDto> activityCategories, Duration timeAgo)
	{
		ZonedDateTime endTime = now().minus(timeAgo);
		ZonedDateTime startTime = endTime.minusMinutes(2);
		return ActivityPayload.createInstance(userAnonDto, deviceAnonDto, startTime, endTime, "n/a", activityCategories);
	}

	protected ActivityPayload makeNetworkPayload(Set<ActivityCategoryDto> activityCategories)
	{
		return makeNetworkPayload(activityCategories, Duration.ofDays(0));
	}

	private ActivityPayload makeNetworkPayload(Set<ActivityCategoryDto> activityCategories, Duration timeAgo)
	{
		ZonedDateTime eventTime = now().minus(timeAgo);
		NetworkActivityDto networkActivity = new NetworkActivityDto(-1, Collections.singleton("n/a"), "n/a",
				Optional.of(eventTime));
		return ActivityPayload.createInstance(userAnonDto, deviceAnonDto, networkActivity, activityCategories);
	}

	private ZonedDateTime now()
	{
		return ZonedDateTime.now().withZoneSameInstant(userAnonZoneId);
	}
}