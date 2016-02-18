/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.Activity;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserService;

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
	private UserService userService;
	@Autowired
	private UserAnonymizedService userAnonymizedService;
	@Autowired
	private MessageService messageService;

	public void analyze(UUID userID, AppActivityDTO appActivity)
	{
		UUID userAnonymizedID = userService.getPrivateUser(userID).getPrivateData().getUserAnonymizedID();
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(userAnonymizedID);
		Set<ActivityCategoryDTO> matchingActivityCategories = activityCategoryService
				.getAppActivityCategories(appActivity.getApplication());
		analyze(new ActivityPayload(appActivity), userAnonymized, matchingActivityCategories);
	}

	public void analyze(NetworkActivityDTO networkActivity)
	{
		UserAnonymizedDTO userAnonymized = userAnonymizedService.getUserAnonymized(networkActivity.getVPNLoginID());
		Set<ActivityCategoryDTO> matchingActivityCategories = activityCategoryService
				.getMatchingActivityCategories(networkActivity.getCategories());
		analyze(new ActivityPayload(networkActivity), userAnonymized, matchingActivityCategories);
	}

	private void analyze(ActivityPayload payload, UserAnonymizedDTO userAnonymized,
			Set<ActivityCategoryDTO> matchingActivityCategories)
	{
		Set<Goal> matchingGoalsOfUser = determineMatchingGoalsForUser(userAnonymized, matchingActivityCategories);
		for (Goal matchingGoalOfUser : matchingGoalsOfUser)
		{
			addOrUpdateActivity(payload, userAnonymized, matchingGoalOfUser);
		}
	}

	private void addOrUpdateActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal)
	{
		Date minEndTime = new Date(payload.endTime.getTime() - yonaProperties.getAnalysisService().getConflictInterval());
		Activity activity = cacheService.fetchLatestActivityForUser(userAnonymized.getID(), matchingGoal.getID(), minEndTime);

		if (activity == null || activity.getEndTime().before(minEndTime))
		{
			activity = createNewActivity(payload, userAnonymized, matchingGoal);
			cacheService.updateLatestActivityForUser(activity);

			if (matchingGoal.isNoGoGoal())
			{
				sendConflictMessageToAllDestinationsOfUser(payload, userAnonymized, activity);
			}
		}
		// Update message only if it is within five seconds to avoid unnecessary cache flushes.
		else if (payload.endTime.getTime() - activity.getEndTime().getTime() >= yonaProperties.getAnalysisService()
				.getUpdateSkipWindow())
		{
			updateLastActivity(payload, userAnonymized, matchingGoal, activity);
			cacheService.updateLatestActivityForUser(activity);
		}
		else if (payload.startTime.before(activity.getStartTime()))
		{
			// can happen if app activity is posted after some network activity is posted during the use of the same app
			// TODO: remove activities after start time and log a new activity?
			// or just overwrite start time of the existing activity, creating possible overlap?
			// we can't undo a goal conflict message!
		}
	}

	private Activity createNewActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal)
	{
		return Activity.createInstance(userAnonymized.getID(), matchingGoal, payload.startTime, payload.endTime);
	}

	private void updateLastActivity(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Goal matchingGoal,
			Activity activity)
	{
		assert userAnonymized.getID().equals(activity.getUserAnonymizedID());
		assert matchingGoal.getID().equals(activity.getGoalID());

		activity.setEndTime(payload.endTime);
	}

	public Set<String> getRelevantSmoothwallCategories()
	{
		return activityCategoryService.getAllActivityCategories().stream().flatMap(g -> g.getSmoothwallCategories().stream())
				.collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfUser(ActivityPayload payload, UserAnonymizedDTO userAnonymized,
			Activity activity)
	{
		GoalConflictMessage selfGoalConflictMessage = sendConflictMessage(payload, userAnonymized, activity,
				userAnonymized.getAnonymousDestination(), null);

		userAnonymized.getBuddyDestinations().stream()
				.forEach(d -> sendConflictMessage(payload, userAnonymized, activity, d, selfGoalConflictMessage));
	}

	private GoalConflictMessage sendConflictMessage(ActivityPayload payload, UserAnonymizedDTO userAnonymized, Activity activity,
			MessageDestinationDTO destination, GoalConflictMessage origin)
	{
		GoalConflictMessage message;
		if (origin == null)
		{
			message = GoalConflictMessage.createInstance(userAnonymized.getID(), activity, payload.url);
		}
		else
		{
			message = GoalConflictMessage.createInstanceFromBuddy(userAnonymized.getID(), origin);
		}
		messageService.sendMessage(message, destination);
		return message;
	}

	private Set<Goal> determineMatchingGoalsForUser(UserAnonymizedDTO userAnonymized,
			Set<ActivityCategoryDTO> matchingActivityCategories)
	{
		Set<UUID> matchingActivityCategoryIDs = matchingActivityCategories.stream().map(ac -> ac.getID())
				.collect(Collectors.toSet());
		Set<Goal> goalsOfUser = userAnonymized.getGoals();
		Set<Goal> matchingGoalsOfUser = goalsOfUser.stream()
				.filter(g -> matchingActivityCategoryIDs.contains(g.getActivityCategory().getID())).collect(Collectors.toSet());
		return matchingGoalsOfUser;
	}

	class ActivityPayload
	{
		public String url;
		public Date startTime;
		public Date endTime;
		public String application;

		public ActivityPayload(NetworkActivityDTO networkActivity)
		{
			this.url = networkActivity.getURL();
			this.startTime = new Date(); // now
			this.endTime = this.startTime;
			this.application = null;
		}

		public ActivityPayload(AppActivityDTO appActivity)
		{
			this.url = null;
			this.startTime = appActivity.getStartTime();
			this.endTime = appActivity.getEndTime();
			this.application = appActivity.getApplication();
		}
	}
}
