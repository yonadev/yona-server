/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;

@Entity
@Table(name = "USERS_ANONYMIZED")
public class UserAnonymized extends EntityWithID
{
	public static UserAnonymizedRepository getRepository()
	{
		return (UserAnonymizedRepository) RepositoryProvider.getRepository(UserAnonymized.class, UUID.class);
	}

	@ManyToOne
	private MessageDestination anonymousDestination;

	@ManyToMany
	private Set<Goal> goals;

	@OneToMany
	private Set<BuddyAnonymized> buddiesAnonymized;

	// Default constructor is required for JPA
	public UserAnonymized()
	{
		super(null);
	}

	public UserAnonymized(UUID id, MessageDestination anonymousDestination, Set<Goal> goals)
	{
		super(id);
		this.anonymousDestination = anonymousDestination;
		this.goals = new HashSet<>(goals);
		this.buddiesAnonymized = new HashSet<>();
	}

	public Set<Goal> getGoals()
	{
		return Collections.unmodifiableSet(goals);
	}

	public MessageDestination getAnonymousDestination()
	{
		return anonymousDestination;
	}

	public Set<MessageDestination> getAllRelatedDestinations()
	{
		Set<MessageDestination> relatedDestinations = new HashSet<>(Arrays.asList(anonymousDestination));
		relatedDestinations.addAll(buddiesAnonymized.stream().map(ba -> ba.getUserAnonymized())
				.map(ua -> ua.getAnonymousDestination()).collect(Collectors.toSet()));
		return relatedDestinations;
	}

	public void addBuddyAnonymized(BuddyAnonymized buddyAnonimized)
	{
		buddiesAnonymized.add(buddyAnonimized);
	}

	public void removeBuddyAnonymized(BuddyAnonymized buddyAnonimized)
	{
		boolean removed = buddiesAnonymized.remove(buddyAnonimized);
		assert removed;
	}

	public UUID getLoginID()
	{
		return getID();
	}

	public static UserAnonymized createInstance(MessageDestination anonymousDestination, Set<Goal> goals)
	{
		return new UserAnonymized(UUID.randomUUID(), anonymousDestination, goals);
	}
}
