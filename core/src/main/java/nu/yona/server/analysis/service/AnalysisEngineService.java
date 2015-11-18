/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.UserAnonymized;

@Service
public class AnalysisEngineService {
	@Autowired
	private GoalService goalService;

	public void analyze(PotentialConflictDTO potentialConflictPayload) {

		UserAnonymized userAnonimized = getUserAnonymizedByID(potentialConflictPayload.getLoginID());
		Set<Goal> conflictingGoalsOfUser = determineConflictingGoalsForUser(userAnonimized,
				potentialConflictPayload.getCategories());
		if (!conflictingGoalsOfUser.isEmpty()) {
			sendConflictMessageToAllDestinationsOfUser(potentialConflictPayload, userAnonimized,
					conflictingGoalsOfUser);
		}
	}

	public Set<String> getRelevantCategories() {
		return goalService.getAllGoals().stream().flatMap(g -> g.getCategories().stream()).collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfUser(PotentialConflictDTO payload, UserAnonymized userAnonymized,
			Set<Goal> conflictingGoalsOfUser) {
		Set<MessageDestination> destinations = userAnonymized.getAllRelatedDestinations();
		destinations.stream().forEach(d -> d.send(createConflictMessage(payload, conflictingGoalsOfUser)));
		destinations.stream().forEach(d -> MessageDestination.getRepository().save(d));
	}

	private GoalConflictMessage createConflictMessage(PotentialConflictDTO payload, Set<Goal> conflictingGoalsOfUser) {
		return GoalConflictMessage.createInstance(payload.getLoginID(), conflictingGoalsOfUser.iterator().next(),
				payload.getURL());
	}

	private Set<Goal> determineConflictingGoalsForUser(UserAnonymized userAnonymized, Set<String> categories) {
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

	private UserAnonymized getUserAnonymizedByID(UUID id) {
		UserAnonymized entity = UserAnonymized.getRepository().findOne(id);
		if (entity == null) {
			throw new LoginIDNotFoundException(id);
		}
		return entity;
	}
}
