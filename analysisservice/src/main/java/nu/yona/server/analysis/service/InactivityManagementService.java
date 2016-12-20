/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.util.LockPool;
import nu.yona.server.util.TimeUtil;

@Service
public class InactivityManagementService
{
	@Autowired(required = false)
	private WeekActivityRepository weekActivityRepository;

	@Autowired(required = false)
	private DayActivityRepository dayActivityRepository;

	@Autowired
	private UserAnonymizedService userAnonymizedService;

	@Autowired
	private GoalService goalService;

	@Autowired
	private LockPool<UUID> userAnonymizedSynchronizer;

	@Transactional
	public void createInactivityEntities(UUID userAnonymizedId, Set<IntervalInactivityDto> intervalInactivities)
	{
		try (LockPool<UUID>.Lock lock = userAnonymizedSynchronizer.lock(userAnonymizedId))
		{
			UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedId);
			createWeekInactivityEntities(userAnonymizedId,
					intervalInactivities.stream().filter(ia -> ia.getTimeUnit() == ChronoUnit.WEEKS).collect(Collectors.toSet()));
			createDayInactivityEntities(userAnonymized,
					intervalInactivities.stream().filter(ia -> ia.getTimeUnit() == ChronoUnit.DAYS).collect(Collectors.toSet()));
		}
	}

	private void createWeekInactivityEntities(UUID userAnonymizedId, Set<IntervalInactivityDto> weekInactivities)
	{
		weekInactivities.stream().forEach(wi -> createWeekInactivity(userAnonymizedId, wi));
	}

	private void createDayInactivityEntities(UserAnonymizedDto userAnonymized, Set<IntervalInactivityDto> dayInactivities)
	{
		dayInactivities.stream()
				.forEach(di -> createDayInactivity(userAnonymized.getId(),
						createWeekInactivity(userAnonymized.getId(), getGoal(userAnonymized, di.getGoalId()).getGoalId(),
								TimeUtil.getStartOfWeek(userAnonymized.getTimeZone(), di.getStartTime())),
						di));
	}

	private GoalDto getGoal(UserAnonymizedDto userAnonymized, UUID goalId)
	{
		return goalService.getGoalForUserAnonymizedId(userAnonymized.getId(), goalId);
	}

	private void createWeekInactivity(UUID userAnonymizedId, IntervalInactivityDto weekInactivity)
	{
		createWeekInactivity(userAnonymizedId, weekInactivity.getGoalId(), weekInactivity.getStartTime());
	}

	private WeekActivity createWeekInactivity(UUID userAnonymizedId, UUID goalId, ZonedDateTime weekStartTime)
	{
		return createInactivity(userAnonymizedId, goalId,
				() -> weekActivityRepository.findOne(userAnonymizedId, weekStartTime.toLocalDate(), goalId),
				(ua, g) -> WeekActivity.createInstance(ua, g, weekStartTime.getZone(), weekStartTime.toLocalDate()),
				(wa) -> wa.getGoal().addWeekActivity(wa));
	}

	private void createDayInactivity(UUID userAnonymizedId, WeekActivity weekActivity, IntervalInactivityDto dayInactivity)
	{
		createInactivity(userAnonymizedId, dayInactivity.getGoalId(),
				() -> dayActivityRepository.findOne(userAnonymizedId, dayInactivity.getStartTime().toLocalDate(),
						dayInactivity.getGoalId()),
				(ua, g) -> DayActivity.createInstance(ua, g, dayInactivity.getStartTime().getZone(),
						dayInactivity.getStartTime().toLocalDate()),
				(da) -> weekActivity.addDayActivity(da));
	}

	private <T, R> T createInactivity(UUID userAnonymizedId, UUID goalId, Supplier<T> existingActivityFinder,
			BiFunction<UserAnonymized, Goal, T> creator, Consumer<T> storer)
	{
		T existingActivity = existingActivityFinder.get();
		if (existingActivity != null)
		{
			return existingActivity;
		}
		UserAnonymized userAnonymized = userAnonymizedService.getUserAnonymizedEntity(userAnonymizedId);
		Goal goal = goalService.getGoalEntityForUserAnonymizedId(userAnonymizedId, goalId);
		T inactivityEntity = creator.apply(userAnonymized, goal);

		storer.accept(inactivityEntity);

		return inactivityEntity;
	}
}
