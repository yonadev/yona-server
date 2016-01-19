/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;

@Service
public class AnalysisEngineService
{
	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private ActivityCategoryService activityCategoryService;
	@Autowired
	private AnalysisEngineCacheService cacheService;
	@Autowired
	private UserAnonymizedService userAnonymizedService;
	@Autowired
	private MessageService messageService;

	public void analyze(PotentialConflictDTO potentialConflictPayload)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(potentialConflictPayload.getVPNLoginID());
		Set<Goal> matchingGoalsOfUser = determineMatchingGoalsForUser(userAnonymized, potentialConflictPayload.getCategories());
		if (!matchingGoalsOfUser.isEmpty())
		{
			addOrUpdateActivities(potentialConflictPayload, userAnonymized, matchingGoalsOfUser);
		}
	}

	private void addOrUpdateActivities(PotentialConflictDTO potentialConflictPayload, UserAnonymizedDTO userAnonymized,
			Set<Goal> matchingGoalsOfUser)
	{
		for (Goal matchingGoalOfUser : matchingGoalsOfUser)
		{
			addOrUpdateActivity(potentialConflictPayload, userAnonymized, matchingGoalOfUser);
		}
	}

	private void addOrUpdateActivity(PotentialConflictDTO payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal)
	{
		Date now = new Date();
		Date minEndTime = new Date(now.getTime() - yonaProperties.getAnalysisService().getConflictInterval());
		Activity activity = cacheService.fetchLatestActivityForUser(payload.getVPNLoginID(), matchingGoal.getID(), minEndTime);

		if (activity == null || activity.getEndTime().before(minEndTime))
		{
			activity = addNewActivity(payload, now, matchingGoal);
			cacheService.updateLatestActivityForUser(activity);

			if (matchingGoal.isNoGoGoal())
			{
				sendConflictMessageToAllDestinationsOfUser(payload, userAnonymized, activity);
			}
		}
		// Update message only if it is within five seconds to avoid unnecessary cache flushes.
		else if (now.getTime() - activity.getEndTime().getTime() >= yonaProperties.getAnalysisService().getUpdateSkipWindow())
		{
			updateLastActivity(payload, now, matchingGoal, activity);
			cacheService.updateLatestActivityForUser(activity);
		}
	}

	private Activity addNewActivity(PotentialConflictDTO payload, Date activityEndTime, Goal matchingGoal)
	{
		return Activity.createInstance(payload.getVPNLoginID(), matchingGoal, activityEndTime);
	}

	private void updateLastActivity(PotentialConflictDTO payload, Date activityEndTime, Goal matchingGoal, Activity activity)
	{
		assert payload.getVPNLoginID().equals(activity.getUserAnonymizedID());
		assert matchingGoal.getID().equals(activity.getGoalID());

		activity.setEndTime(activityEndTime);
	}

	public Set<String> getRelevantSmoothwallCategories()
	{
		return activityCategoryService.getAllActivityCategories().stream().flatMap(g -> g.getSmoothwallCategories().stream())
				.collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfUser(PotentialConflictDTO payload, UserAnonymizedDTO userAnonymized,
			Activity activity)
	{
		GoalConflictMessage selfGoalConflictMessage = sendConflictMessage(payload, activity,
				userAnonymized.getAnonymousDestination(), null);

		userAnonymized.getBuddyDestinations().stream()
				.forEach(d -> sendConflictMessage(payload, activity, d, selfGoalConflictMessage));
	}

	private GoalConflictMessage sendConflictMessage(PotentialConflictDTO payload, Activity activity,
			MessageDestinationDTO destination, GoalConflictMessage origin)
	{
		GoalConflictMessage message;
		if (origin == null)
		{
			message = GoalConflictMessage.createInstance(payload.getVPNLoginID(), activity, payload.getURL());
		}
		else
		{
			message = GoalConflictMessage.createInstanceFromBuddy(payload.getVPNLoginID(), origin);
		}
		messageService.sendMessage(message, destination);
		return message;
	}

	private Set<Goal> determineMatchingGoalsForUser(UserAnonymizedDTO userAnonymized, Set<String> categories)
	{
		Set<ActivityCategory> allActivityCategories = activityCategoryService.getAllActivityCategoryEntities();
		Set<ActivityCategory> matchingActivityCategories = allActivityCategories.stream().filter(ac -> {
			Set<String> acSmoothwallCategories = new HashSet<>(ac.getSmoothwallCategories());
			acSmoothwallCategories.retainAll(categories);
			return !acSmoothwallCategories.isEmpty();
		}).collect(Collectors.toSet());
		Set<Goal> goalsOfUser = userAnonymized.getGoals();
		Set<Goal> matchingGoalsOfUser = goalsOfUser.stream()
				.filter(g -> matchingActivityCategories.contains(g.getActivityCategory())).collect(Collectors.toSet());
		return matchingGoalsOfUser;
	}
}
