/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.DayActivity;
import nu.yona.server.analysis.entities.DayActivityRepository;
import nu.yona.server.analysis.entities.WeekActivity;
import nu.yona.server.analysis.entities.WeekActivityRepository;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.util.LockPool;

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
	public void createInactivityEntities(UUID userAnonymizedId, Set<IntervalInactivityDTO> intervalInactivities)
	{
		try (LockPool<UUID>.Lock lock = userAnonymizedSynchronizer.lock(userAnonymizedId))
		{
			createWeekInactivityEntities(userAnonymizedId,
					intervalInactivities.stream().filter(ia -> ia.getTimeUnit() == ChronoUnit.WEEKS).collect(Collectors.toSet()));
			createDayInactivityEntities(userAnonymizedId,
					intervalInactivities.stream().filter(ia -> ia.getTimeUnit() == ChronoUnit.DAYS).collect(Collectors.toSet()));
		}
	}

	private void createWeekInactivityEntities(UUID userAnonymizedId, Set<IntervalInactivityDTO> weekInactivities)
	{
		weekInactivities.stream().forEach(wi -> createWeekInactivity(userAnonymizedId, wi));
	}

	private void createDayInactivityEntities(UUID userAnonymizedId, Set<IntervalInactivityDTO> weekInactivities)
	{
		weekInactivities.stream().forEach(wi -> createDayInactivity(userAnonymizedId, wi));
	}

	private void createWeekInactivity(UUID userAnonymizedId, IntervalInactivityDTO weekInactivity)
	{
		createInactivity(userAnonymizedId, weekInactivity,
				() -> weekActivityRepository.findOne(userAnonymizedId, weekInactivity.getStartTime().toLocalDate(),
						weekInactivity.getGoalId()),
				(ua, g) -> WeekActivity.createInstance(ua, g, weekInactivity.getStartTime().getZone(),
						weekInactivity.getStartTime().toLocalDate()),
				weekActivityRepository);
	}

	private void createDayInactivity(UUID userAnonymizedId, IntervalInactivityDTO dayInactivity)
	{
		createInactivity(userAnonymizedId, dayInactivity,
				() -> dayActivityRepository.findOne(userAnonymizedId, dayInactivity.getStartTime().toLocalDate(),
						dayInactivity.getGoalId()),
				(ua, g) -> DayActivity.createInstance(ua, g, dayInactivity.getStartTime().getZone(),
						dayInactivity.getStartTime().toLocalDate()),
				dayActivityRepository);
	}

	private <T, R> void createInactivity(UUID userAnonymizedId, IntervalInactivityDTO intervalInactivity,
			Supplier<T> existingActivityFinder, BiFunction<UserAnonymized, Goal, T> creator, CrudRepository<T, UUID> repository)
	{
		T existingActivity = existingActivityFinder.get();
		if (existingActivity != null)
		{
			return;
		}
		UserAnonymized userAnonymized = userAnonymizedService.getUserAnonymizedEntity(userAnonymizedId);
		Goal goal = goalService.getGoalEntityForUserAnonymizedId(userAnonymizedId, intervalInactivity.getGoalId());
		T inactivityEntity = creator.apply(userAnonymized, goal);

		repository.save(inactivityEntity);
	}
}
