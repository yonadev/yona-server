/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import nu.yona.server.Translator;
import nu.yona.server.crypto.pubkey.PublicKeyUtil;
import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.device.entities.DeviceAnonymized.OperatingSystem;
import nu.yona.server.device.service.DeviceAnonymizedDto;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.BudgetGoal;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.entities.TimeZoneGoal;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.util.TimeUtil;

@RunWith(Parameterized.class)
public class AnalysisEngineService_determineRelevantGoalsTest
{
	private OperatingSystem operatingSystem;
	private boolean isIOs;
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

	public AnalysisEngineService_determineRelevantGoalsTest(String operatingSystem, boolean isIOs)
	{
		this.operatingSystem = OperatingSystem.valueOf(operatingSystem);
		this.isIOs = isIOs;
	}

	@Parameters
	public static Collection<Object[]> data()
	{
		return Arrays.asList(new Object[][] { { "IOS", true }, { "ANDROID", false } });
	}

	@Before
	public void setUp()
	{
		initMocks(this);

		LocalDateTime yesterday = TimeUtil.utcNow().minusDays(1);
		LocalDateTime dayBeforeYesterday = yesterday.minusDays(1);
		gamblingGoal = BudgetGoal.createNoGoInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("gambling"), false,
						new HashSet<>(Arrays.asList("poker", "lotto")), new HashSet<>(Arrays.asList("Poker App", "Lotto App")),
						usString("Descr")));
		newsGoal = BudgetGoal.createNoGoInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("news"), false,
						new HashSet<>(Arrays.asList("refdag", "bbc")), Collections.emptySet(), usString("Descr")));
		gamingGoal = BudgetGoal.createNoGoInstance(dayBeforeYesterday, ActivityCategory.createInstance(UUID.randomUUID(),
				usString("gaming"), false, new HashSet<>(Arrays.asList("games")), Collections.emptySet(), usString("Descr")));
		socialGoal = TimeZoneGoal.createInstance(dayBeforeYesterday,
				ActivityCategory.createInstance(UUID.randomUUID(), usString("social"), false,
						new HashSet<>(Arrays.asList("social")), Collections.emptySet(), usString("Descr")),
				Collections.emptyList());
		shoppingGoal = BudgetGoal.createInstance(dayBeforeYesterday, ActivityCategory.createInstance(UUID.randomUUID(),
				usString("shopping"), false, new HashSet<>(Arrays.asList("webshop")), Collections.emptySet(), usString("Descr")),
				1);
		shoppingGoalHistoryItem = shoppingGoal.cloneAsHistoryItem(yesterday);

		// Set up UserAnonymized instance.
		MessageDestination anonMessageDestinationEntity = MessageDestination
				.createInstance(PublicKeyUtil.generateKeyPair().getPublic());
		deviceAnonEntity = DeviceAnonymized.createInstance(0, operatingSystem, "Unknown");
		userAnonEntity = UserAnonymized.createInstance(anonMessageDestinationEntity);
		Arrays.asList(gamblingGoal, gamingGoal, socialGoal, shoppingGoal, shoppingGoalHistoryItem).stream()
				.forEach(g -> userAnonEntity.addGoal(g));
		userAnonEntity.addDeviceAnonymized(deviceAnonEntity);
		userAnonDto = UserAnonymizedDto.createInstance(userAnonEntity);
		deviceAnonDto = DeviceAnonymizedDto.createInstance(deviceAnonEntity);
		userAnonZoneId = userAnonDto.getTimeZone();
	}

	private Map<Locale, String> usString(String string)
	{
		return Collections.singletonMap(Translator.EN_US_LOCALE, string);
	}

	@Test
	public void determineRelevantGoals_appActivityNotAUserGoal_emptySet()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(), makeCategorySet(newsGoal));
		assertThat(relevantGoals, empty());
	}

	@Test
	public void determineRelevantGoals_appActivityOneUserGoal_userGoal()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(), makeCategorySet(socialGoal));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(socialGoal)));
	}

	@Test
	public void determineRelevantGoals_appActivityTwoUserGoals_bothUserGoals()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(),
				makeCategorySet(socialGoal, gamblingGoal));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(socialGoal), GoalDto.createInstance(gamblingGoal)));
	}

	@Test
	public void determineRelevantGoals_appActivityTwoGoalsOneUserGoal_userGoal()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(),
				makeCategorySet(newsGoal, gamblingGoal));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(gamblingGoal)));
	}

	@Test
	public void determineRelevantGoals_appActivityBeforeGoalStart_emptySet()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(Duration.ofDays(3)),
				makeCategorySet(socialGoal));
		assertThat(relevantGoals, empty());
	}

	@Test
	public void determineRelevantGoals_appActivityOnGoalWithHistory_oneGoalReturned()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeAppPayload(Duration.ofDays(1)),
				makeCategorySet(shoppingGoal));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(shoppingGoal)));
	}

	@Test
	public void determineRelevantGoals_networkActivityOneNoGoUserGoal_userGoal()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeNetworkPayload(),
				makeCategorySet(gamingGoal));
		assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(gamingGoal)));
	}

	@Test
	public void determineRelevantGoals_networkActivityOneOptionalUserGoal_userGoalOnIOs()
	{
		Set<GoalDto> relevantGoals = AnalysisEngineService.determineRelevantGoals(makeNetworkPayload(),
				makeCategorySet(socialGoal));
		if (isIOs)
		{
			assertThat(relevantGoals, containsInAnyOrder(GoalDto.createInstance(socialGoal)));
		}
		else
		{
			assertThat(relevantGoals, empty());
		}
	}

	private Set<ActivityCategoryDto> makeCategorySet(Goal... goals)
	{
		return Arrays.asList(goals).stream().map(Goal::getActivityCategory).map(ActivityCategoryDto::createInstance)
				.collect(Collectors.toSet());
	}

	private ActivityPayload makeAppPayload()
	{
		return makeAppPayload(Duration.ofDays(0));
	}

	private ActivityPayload makeAppPayload(Duration timeAgo)
	{
		ZonedDateTime endTime = now().minus(timeAgo);
		ZonedDateTime startTime = endTime.minusMinutes(2);
		return ActivityPayload.createInstance(userAnonDto, deviceAnonDto, startTime, endTime, "n/a");
	}

	private ActivityPayload makeNetworkPayload()
	{
		return makeNetworkPayload(Duration.ofDays(0));
	}

	private ActivityPayload makeNetworkPayload(Duration timeAgo)
	{
		ZonedDateTime eventTime = now().minus(timeAgo);
		NetworkActivityDto networkActivity = new NetworkActivityDto(-1, Collections.singleton("n/a"), "n/a",
				Optional.of(eventTime));
		return ActivityPayload.createInstance(userAnonDto, deviceAnonDto, networkActivity);
	}

	private ZonedDateTime now()
	{
		return ZonedDateTime.now().withZoneSameInstant(userAnonZoneId);
	}
}