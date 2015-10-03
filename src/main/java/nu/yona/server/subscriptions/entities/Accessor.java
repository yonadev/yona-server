/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.model.RepositoryProvider;

@Entity
@Table(name = "ACCESSORS")
public class Accessor {
	public static AccessorRepository getRepository() {
		return (AccessorRepository) RepositoryProvider.getRepository(Accessor.class, UUID.class);
	}

	@Id
	@Type(type = "uuid-char")
	private UUID id;

	@ManyToMany
	private Set<Goal> goals;

	@ManyToMany
	private Set<MessageDestination> destinations;

	public Accessor() {
		// Default constructor is required for JPA
	}

	public Accessor(UUID id, Set<Goal> goals) {
		this.id = id;
		this.goals = new HashSet<>(goals);
		this.destinations = new HashSet<>();
	}

	public UUID getID() {
		return id;
	}

	public Set<Goal> getGoals() {
		return Collections.unmodifiableSet(goals);
	}

	public Set<MessageDestination> getDestinations() {
		return Collections.unmodifiableSet(destinations);
	}

	public static Accessor createInstance(Set<Goal> goals) {
		return new Accessor(UUID.randomUUID(), goals);
	}

	public void addDestination(MessageDestination destination) {
		destinations.add(destination);
	}
}
