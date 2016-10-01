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
import nu.yona.server.analysis.service.UserAnonymizedSynchronizer.Lock;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserAnonymizedService;

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
	private UserAnonymizedSynchronizer userAnonymizedSynchronizer;

	@Transactional
	public void createInactivityEntities(UUID userAnonymizedID, Set<IntervalInactivity> intervalInactivities)
	{
		try (Lock lock = userAnonymizedSynchronizer.lock(userAnonymizedID))
		{
			createWeekInactivityEntities(userAnonymizedID,
					intervalInactivities.stream().filter(ia -> ia.getTimeUnit() == ChronoUnit.WEEKS).collect(Collectors.toSet()));
			createDayInactivityEntities(userAnonymizedID,
					intervalInactivities.stream().filter(ia -> ia.getTimeUnit() == ChronoUnit.DAYS).collect(Collectors.toSet()));
		}
	}

	private void createWeekInactivityEntities(UUID userAnonymizedID, Set<IntervalInactivity> weekInactivities)
	{
		weekInactivities.stream().forEach(wi -> createWeekInactivity(userAnonymizedID, wi));
	}

	private void createDayInactivityEntities(UUID userAnonymizedID, Set<IntervalInactivity> weekInactivities)
	{
		weekInactivities.stream().forEach(wi -> createDayInactivity(userAnonymizedID, wi));
	}

	private void createWeekInactivity(UUID userAnonymizedID, IntervalInactivity weekInactivity)
	{
		createInactivity(userAnonymizedID, weekInactivity,
				() -> weekActivityRepository.findOne(userAnonymizedID, weekInactivity.getStartTime().toLocalDate(),
						weekInactivity.getGoalID()),
				(ua, g) -> WeekActivity.createInstance(ua, g, weekInactivity.getStartTime()), weekActivityRepository);
	}

	private void createDayInactivity(UUID userAnonymizedID, IntervalInactivity dayInactivity)
	{
		createInactivity(userAnonymizedID, dayInactivity,
				() -> dayActivityRepository.findOne(userAnonymizedID, dayInactivity.getStartTime().toLocalDate(),
						dayInactivity.getGoalID()),
				(ua, g) -> DayActivity.createInstance(ua, g, dayInactivity.getStartTime()), dayActivityRepository);
	}

	private <T, R> void createInactivity(UUID userAnonymizedID, IntervalInactivity intervalInactivity,
			Supplier<T> existingActivityFinder, BiFunction<UserAnonymized, Goal, T> creator, CrudRepository<T, UUID> repository)
	{
		T existingActivity = existingActivityFinder.get();
		if (existingActivity != null)
		{
			return;
		}
		UserAnonymized userAnonymized = userAnonymizedService.getUserAnonymizedEntity(userAnonymizedID);
		Goal goal = goalService.getGoalEntityForUserAnonymizedID(userAnonymizedID, intervalInactivity.getGoalID());
		T inactivityEntity = creator.apply(userAnonymized, goal);

		repository.save(inactivityEntity);
	}
}
