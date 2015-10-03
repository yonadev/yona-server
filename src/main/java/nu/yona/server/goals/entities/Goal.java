/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.model.RepositoryProvider;

@Entity
@Table(name = "GOALS")
public class Goal {
	public static GoalRepository getRepository() {
		return (GoalRepository) RepositoryProvider.getRepository(Goal.class, UUID.class);
	}

	@Id
	@Type(type = "uuid-char")
	private UUID id;

	@Column(unique = true)
	private String name;

	@ElementCollection
	private Set<String> categories;

	public Goal() {
		// Default constructor is required for JPA
	}

	public Goal(UUID id, String name, Set<String> categories) {
		this.id = id;
		this.name = name;
		this.categories = new HashSet<>(categories);
	}

	public String getName() {
		return name;
	}

	public Set<String> getCategories() {
		return Collections.unmodifiableSet(categories);
	}

	public UUID getID() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setCategories(Set<String> categories) {
		this.categories = new HashSet<>(categories);
	}

	public static Goal createInstance(String name, Set<String> categories) {
		return new Goal(UUID.randomUUID(), name, categories);
	}
}
