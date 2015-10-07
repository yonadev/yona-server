/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.goals.entities.Goal;

@Service
public class GoalService {
	public GoalDTO getGoal(UUID id) {
		Goal goalEntity = getEntityByID(id);
		return GoalDTO.createInstance(goalEntity);
	}

	public Set<GoalDTO> getAllGoals() {
		Set<GoalDTO> goals = new HashSet<GoalDTO>();
		for (Goal goal : Goal.getRepository().findAll()) {
			goals.add(GoalDTO.createInstance(goal));
		}
		return goals;
	}

	@Transactional
	public GoalDTO addGoal(GoalDTO goalDTO) {
		return GoalDTO.createInstance(Goal.getRepository().save(goalDTO.createGoalEntity()));
	}

	@Transactional
	public GoalDTO updateGoal(UUID id, GoalDTO goalDTO) {
		Goal originalGoalEntity = getEntityByID(id);
		return GoalDTO.createInstance(Goal.getRepository().save(goalDTO.updateGoal(originalGoalEntity)));
	}

	@Transactional
	public void deleteGoal(UUID id) {
		Goal.getRepository().delete(id);
	}

	private Goal getEntityByID(UUID id) {
		Goal entity = Goal.getRepository().findOne(id);
		if (entity == null) {
			throw new GoalNotFoundException(id);
		}
		return entity;
	}

	public Set<Goal> getAllGoalEntities() {
		Set<Goal> goals = new HashSet<Goal>();
		for (Goal goal : Goal.getRepository().findAll()) {
			goals.add(goal);
		}
		return goals;
	}
}
