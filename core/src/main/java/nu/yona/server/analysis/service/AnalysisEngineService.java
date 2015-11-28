/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Service
public class AnalysisEngineService
{
	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private GoalService goalService;
	@Autowired
	private AnalysisEngineCacheService cacheService;

	public void analyze(PotentialConflictDTO potentialConflictPayload)
	{
		UserAnonymized userAnonimized = getUserAnonymizedByID(potentialConflictPayload.getVPNLoginID());
		Set<Goal> conflictingGoalsOfUser = determineConflictingGoalsForUser(userAnonimized,
				potentialConflictPayload.getCategories());
		if (!conflictingGoalsOfUser.isEmpty())
		{
			sendConflictMessageToAllDestinationsOfUser(potentialConflictPayload, userAnonimized, conflictingGoalsOfUser);
		}
	}

	public Set<String> getRelevantCategories()
	{
		return goalService.getAllGoals().stream().flatMap(g -> g.getCategories().stream()).collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfUser(PotentialConflictDTO payload, UserAnonymized userAnonymized,
			Set<Goal> conflictingGoalsOfUser)
	{
		sendOrUpdateConflictMessage(payload, conflictingGoalsOfUser, userAnonymized.getAnonymousDestination(), false);

		userAnonymized.getAllRelatedDestinations().stream()
				.forEach(d -> sendOrUpdateConflictMessage(payload, conflictingGoalsOfUser, d, true));
	}

	private GoalConflictMessage sendOrUpdateConflictMessage(PotentialConflictDTO payload, Set<Goal> conflictingGoalsOfUser,
			MessageDestination destination, boolean isFromBuddy)
	{
		Date now = new Date();
		Date minEndTime = new Date(now.getTime() - yonaProperties.getAnalysisService().getConflictInterval());
		Goal conflictingGoal = conflictingGoalsOfUser.iterator().next();
		GoalConflictMessage message = cacheService.fetchLatestGoalConflictMessageForUser(payload.getVPNLoginID(),
				conflictingGoal.getID(), destination, minEndTime);

		if (message == null || message.getEndTime().before(minEndTime))
		{
			message = sendNewGoalConflictMessage(payload, conflictingGoal, destination, isFromBuddy);
			cacheService.updateLatestGoalConflictMessageForUser(message, destination);
		}
		// Update message only if it is within five seconds to avoid unnecessary cache flushes.
		else if (now.getTime() - message.getEndTime().getTime() >= yonaProperties.getAnalysisService().getUpdateSkipWindow())
		{
			updateLastGoalConflictMessage(payload, now, conflictingGoal, message);
			cacheService.updateLatestGoalConflictMessageForUser(message, destination);
		}

		return message;
	}

	private GoalConflictMessage sendNewGoalConflictMessage(PotentialConflictDTO payload, Goal conflictingGoal,
			MessageDestination destination, boolean isFromBuddy)
	{
		GoalConflictMessage message = GoalConflictMessage.createInstance(payload.getVPNLoginID(), conflictingGoal,
				payload.getURL(), isFromBuddy);
		destination.send(message);
		return message;
	}

	private void updateLastGoalConflictMessage(PotentialConflictDTO payload, Date messageEndTime, Goal conflictingGoal,
			GoalConflictMessage message)
	{
		assert payload.getVPNLoginID().equals(message.getRelatedVPNLoginID());
		assert conflictingGoal.getID().equals(message.getGoal().getID());

		message.setEndTime(messageEndTime);
	}

	private Set<Goal> determineConflictingGoalsForUser(UserAnonymized userAnonymized, Set<String> categories)
	{
		Set<Goal> allGoals = goalService.getAllGoalEntities();
		Set<Goal> conflictingGoals = allGoals.stream().filter(g -> {
			Set<String> goalCategories = new HashSet<>(g.getCategories());
			goalCategories.retainAll(categories);
			return !goalCategories.isEmpty();
		}).collect(Collectors.toSet());
		Set<Goal> goalsOfUser = userAnonymized.getGoals();
		Set<Goal> conflictingGoalsOfUser = conflictingGoals.stream().filter(g -> goalsOfUser.contains(g))
				.collect(Collectors.toSet());
		return conflictingGoalsOfUser;
	}

	private UserAnonymized getUserAnonymizedByID(UUID id)
	{
		UserAnonymized entity = UserAnonymized.getRepository().findOne(id);
		if (entity == null)
		{
			throw InvalidDataException.vpnLoginIDNotFound(id);
		}
		return entity;
	}
}
