/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.entities.Goal;

@JsonRootName("goal")
public class GoalDTO {
	private final UUID id;
	private final String name;
	private Set<String> categories;

	private GoalDTO(UUID id, String name, Set<String> categories) {
		this.id = id;
		this.name = name;
		this.categories = new HashSet<>(categories);
	}

	@JsonCreator
	public GoalDTO(@JsonProperty("name") String name,
			@JsonProperty("categories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> categories) {
		this(null, name, categories);
	}

	@JsonIgnore
	public UUID getID() {
		return id;
	}

	public String getName() {
		return name;
	}

	@JsonProperty("categories")
	public Set<String> getCategories() {
		return Collections.unmodifiableSet(categories);
	}

	public static GoalDTO createInstance(Goal goalEntity) {
		return new GoalDTO(goalEntity.getID(), goalEntity.getName(), goalEntity.getCategories());
	}

	public Goal createGoalEntity() {
		return Goal.createInstance(name, categories);
	}

	public Goal updateGoal(Goal originalGoalEntity) {
		originalGoalEntity.setName(name);
		originalGoalEntity.setCategories(categories);

		return originalGoalEntity;
	}
}
