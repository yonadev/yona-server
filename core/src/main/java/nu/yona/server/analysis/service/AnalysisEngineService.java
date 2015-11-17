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

		UserAnonymized accessor = getAccessorByID(potentialConflictPayload.getAccessorID());
		Set<Goal> conflictingGoalsOfAccessor = determineConflictingGoalsForAccessor(accessor,
				potentialConflictPayload.getCategories());
		if (!conflictingGoalsOfAccessor.isEmpty()) {
			sendConflictMessageToAllDestinationsOfAccessor(potentialConflictPayload, accessor,
					conflictingGoalsOfAccessor);
		}
	}

	public Set<String> getRelevantCategories() {
		return goalService.getAllGoals().stream().flatMap(g -> g.getCategories().stream()).collect(Collectors.toSet());
	}

	private void sendConflictMessageToAllDestinationsOfAccessor(PotentialConflictDTO payload,
			UserAnonymized userAnonymized, Set<Goal> conflictingGoalsOfAccessor) {
		Set<MessageDestination> destinations = userAnonymized.getAllRelatedDestinations();
		destinations.stream().forEach(d -> d.send(createConflictMessage(payload, conflictingGoalsOfAccessor)));
		destinations.stream().forEach(d -> MessageDestination.getRepository().save(d));
	}

	private GoalConflictMessage createConflictMessage(PotentialConflictDTO payload,
			Set<Goal> conflictingGoalsOfAccessor) {
		return GoalConflictMessage.createInstance(payload.getAccessorID(), conflictingGoalsOfAccessor.iterator().next(),
				payload.getURL());
	}

	private Set<Goal> determineConflictingGoalsForAccessor(UserAnonymized accessor, Set<String> categories) {
		Set<Goal> allGoals = goalService.getAllGoalEntities();
		Set<Goal> conflictingGoals = allGoals.stream().filter(g -> {
			Set<String> goalCategories = new HashSet<>(g.getCategories());
			goalCategories.retainAll(categories);
			return !goalCategories.isEmpty();
		}).collect(Collectors.toSet());
		Set<Goal> goalsOfAccessor = accessor.getGoals();
		Set<Goal> conflictingGoalsOfAccessor = conflictingGoals.stream().filter(g -> goalsOfAccessor.contains(g))
				.collect(Collectors.toSet());
		return conflictingGoalsOfAccessor;
	}

	private UserAnonymized getAccessorByID(UUID id) {
		UserAnonymized entity = UserAnonymized.getRepository().findOne(id);
		if (entity == null) {
			throw new AccessorNotFoundException(id);
		}
		return entity;
	}
}
