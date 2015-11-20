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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Service
public class AnalysisEngineService
{
	@Autowired
	private GoalService goalService;
	@Value("${yona.analysisservice.conflict.interval}")
	private int conflictInterval;

	public void analyze(PotentialConflictDTO potentialConflictPayload)
	{

		UserAnonymized userAnonimized = getUserAnonymizedByID(potentialConflictPayload.getLoginID());
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
		Set<MessageDestination> destinations = userAnonymized.getAllRelatedDestinations();
		destinations.stream().forEach(d -> sendOrUpdateConflictMessage(payload, conflictingGoalsOfUser, d));
		destinations.stream().forEach(d -> MessageDestination.getRepository().save(d));
	}

	private GoalConflictMessage sendOrUpdateConflictMessage(PotentialConflictDTO payload, Set<Goal> conflictingGoalsOfUser,
			MessageDestination destination)
	{
		Date now = new Date();
		Goal conflictingGoal = conflictingGoalsOfUser.iterator().next();
		GoalConflictMessage message = GoalConflictMessage.createInstance(payload.getLoginID(), conflictingGoal, payload.getURL());

		// Encrypt the message, so we get encrypted related user and goal IDs.
		destination.encryptMessage(message);

		GoalConflictMessage existingMessage = GoalConflictMessage.getGoalConflictMessageRepository()
				.findLatestGoalConflictMessageFromDestination(/* message.getRelatedUserAnonymizedIDCiphertext(),
						message.getGoalIDCiphertext(), */destination.getID(), GoalConflictMessage.class);

		if (existingMessage != null && now.getTime() - existingMessage.getEndTime().getTime() <= conflictInterval * 1000L)
		{
			existingMessage.setEndTime(now);
			return existingMessage;
		}

		destination.send(message);

		return message;
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
			throw new LoginIDNotFoundException(id);
		}
		return entity;
	}
}
