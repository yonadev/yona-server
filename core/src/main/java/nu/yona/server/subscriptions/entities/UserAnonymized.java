/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Where;

import nu.yona.server.device.entities.DeviceAnonymized;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;

@Entity
@Table(name = "USERS_ANONYMIZED")
public class UserAnonymized extends EntityWithUuid
{
	private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Amsterdam");
	private LocalDate lastMonitoredActivityDate;

	@OneToOne(fetch = FetchType.LAZY)
	private MessageDestination anonymousDestination;

	@OneToMany(mappedBy = "userAnonymized", cascade = CascadeType.ALL, orphanRemoval = true)
	@Where(clause = "end_time is null") // The history items have the user anonymized ID set, so they would appear in this
	// collection if not explicitly excluded
	@BatchSize(size = 20)
	private Set<Goal> goals;

	/**
	 * The BuddyAnonymized entities owned by this user
	 */
	@OneToMany(mappedBy = "owningUserAnonymized", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<BuddyAnonymized> buddiesAnonymized;

	@OneToMany(mappedBy = "userAnonymized", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<DeviceAnonymized> devicesAnonymized;

	@Transient
	private boolean isGoalPreloadDone;

	// Default constructor is required for JPA
	public UserAnonymized()
	{
		super(null);
	}

	private UserAnonymized(UUID id, MessageDestination anonymousDestination, Set<Goal> goals)
	{
		super(id);
		this.anonymousDestination = anonymousDestination;
		this.goals = new HashSet<>(goals);
		this.buddiesAnonymized = new HashSet<>();
		this.devicesAnonymized = new HashSet<>();
	}

	public static UserAnonymizedRepository getRepository()
	{
		return (UserAnonymizedRepository) RepositoryProvider.getRepository(UserAnonymized.class, UUID.class);
	}

	public static UserAnonymized createInstance(MessageDestination anonymousDestination, Set<Goal> goals)
	{
		return new UserAnonymized(UUID.randomUUID(), anonymousDestination, goals);
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return Optional.ofNullable(lastMonitoredActivityDate);
	}

	public void setLastMonitoredActivityDate(LocalDate lastMonitoredActivityDate)
	{
		this.lastMonitoredActivityDate = Objects.requireNonNull(lastMonitoredActivityDate);
	}

	public Set<Goal> getGoals()
	{
		return Collections.unmodifiableSet(goals);
	}

	public MessageDestination getAnonymousDestination()
	{
		return anonymousDestination;
	}

	public void clearAnonymousDestination()
	{
		anonymousDestination = null;
	}

	public void addBuddyAnonymized(BuddyAnonymized buddyAnonimized)
	{
		buddiesAnonymized.add(buddyAnonimized);
		buddyAnonimized.setOwningUserAnonymized(this);
	}

	public void removeBuddyAnonymized(BuddyAnonymized buddyAnonimized)
	{
		boolean removed = buddiesAnonymized.remove(buddyAnonimized);
		assert removed;
		buddyAnonimized.clearOwningUserAnonymized();
	}

	public Set<BuddyAnonymized> getBuddiesAnonymized()
	{
		return buddiesAnonymized;
	}

	public void addGoal(Goal goal)
	{
		goal.setUserAnonymized(this);
		goals.add(goal);
	}

	public void removeGoal(Goal goal)
	{
		if (!goals.remove(goal))
		{
			throw new IllegalArgumentException("Goal was not found");
		}
	}

	public void addDeviceAnonymized(DeviceAnonymized deviceAnonimized)
	{
		devicesAnonymized.add(deviceAnonimized);
		deviceAnonimized.setUserAnonymized(this);
	}

	public void removeDeviceAnonymized(DeviceAnonymized deviceAnonimized)
	{
		boolean removed = devicesAnonymized.remove(deviceAnonimized);
		assert removed;
		deviceAnonimized.clearUserAnonymized();
	}

	public Set<DeviceAnonymized> getDevicesAnonymized()
	{
		return devicesAnonymized;
	}

	public ZoneId getTimeZone()
	{
		return DEFAULT_TIME_ZONE;
	}

	public void preloadGoals()
	{
		if (!isGoalPreloadDone)
		{
			Goal.getRepository().findByUserAnonymizedId(getId()); // Preload in one go
			isGoalPreloadDone = true;
		}
	}

	public Set<Goal> getGoalsIncludingHistoryItems()
	{
		preloadGoals();
		Set<Goal> activeGoals = getGoals();
		Set<Goal> historyItems = getGoalHistoryItems(activeGoals);
		Set<Goal> allGoals = new HashSet<>(activeGoals);
		allGoals.addAll(historyItems);
		return allGoals;
	}

	private static Set<Goal> getGoalHistoryItems(Set<Goal> activeGoals)
	{
		Set<Goal> historyItems = new HashSet<>();
		activeGoals.stream().forEach(g -> {
			Optional<Goal> historyItem = g.getPreviousVersionOfThisGoal();
			while (historyItem.isPresent())
			{
				historyItems.add(historyItem.get());
				historyItem = historyItem.get().getPreviousVersionOfThisGoal();
			}
		});
		return historyItems;
	}
}
