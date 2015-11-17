/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
		return GoalDTO.createInstance(updateGoal(originalGoalEntity, goalDTO));
	}

	private Goal updateGoal(Goal goalEntity, GoalDTO goalDTO) {
		return Goal.getRepository().save(goalDTO.updateGoal(goalEntity));
	}

	@Transactional
	public void deleteGoal(UUID id) {
		Goal.getRepository().delete(id);
	}

	private void deleteGoal(Goal goalEntity) {
		Goal.getRepository().delete(goalEntity);
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

	@Transactional
	public void importGoals(Set<GoalDTO> goalDTOs) {
		Set<Goal> goalsInRepository = getAllGoalEntities();				
		deleteRemovedGoals(goalsInRepository, goalDTOs);
		addOrUpdateNewGoals(goalDTOs, goalsInRepository);
	}

	private void addOrUpdateNewGoals(Set<GoalDTO> goalDTOs, Set<Goal> goalsInRepository) {
		Map<String, Goal> goalsInRepositoryMap = new HashMap<String, Goal>();
		for(Goal goalInRepository : goalsInRepository) goalsInRepositoryMap.put(goalInRepository.getName(), goalInRepository);
		
		for(GoalDTO goalDTO : goalDTOs) {
			Goal goalInRepository = goalsInRepositoryMap.get(goalDTO.getName());
			if(goalInRepository == null) {
				addGoal(goalDTO);
			} else {
				if(!goalInRepository.getCategories().equals(goalDTO.getCategories()))
				{
					updateGoal(goalInRepository, goalDTO);
				}
			}
		}
	}

	private void deleteRemovedGoals(Set<Goal> goalsInRepository, Set<GoalDTO> goalDTOs) {
		Map<String, GoalDTO> goalDTOsMap = new HashMap<String, GoalDTO>();
		for(GoalDTO goalDTO : goalDTOs) goalDTOsMap.put(goalDTO.getName(), goalDTO);
		
		for(Goal goalInRepository : goalsInRepository) {
			if(!goalDTOsMap.containsKey(goalInRepository.getName())) {
				deleteGoal(goalInRepository);
			}
		}
	}
}
