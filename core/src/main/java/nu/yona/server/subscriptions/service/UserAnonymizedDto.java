/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.messaging.service.MessageDestinationDto;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymized;

public class UserAnonymizedDto implements Serializable
{
	private static final long serialVersionUID = 5569303515806259326L;

	private final UUID id;

	private final LocalDate lastMonitoredActivityDate;
	private final Set<GoalDto> goals;
	private final MessageDestinationDto anonymousMessageDestination;
	private final Set<UUID> buddyAnonymizedIds;

	private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Amsterdam");

	public UserAnonymizedDto(UUID id, Optional<LocalDate> lastMonitoredActivityDate, Set<GoalDto> goals,
			MessageDestinationDto anonymousMessageDestination, Set<UUID> buddyAnonymizedIds)
	{
		this.id = id;
		this.lastMonitoredActivityDate = lastMonitoredActivityDate.orElse(null);
		this.goals = new HashSet<>(goals);
		this.anonymousMessageDestination = anonymousMessageDestination;
		this.buddyAnonymizedIds = buddyAnonymizedIds;
	}

	public static UserAnonymizedDto createInstance(UserAnonymized entity)
	{
		return new UserAnonymizedDto(entity.getId(), entity.getLastMonitoredActivityDate(), getGoalsIncludingHistoryItems(entity),
				MessageDestinationDto.createInstance(entity.getAnonymousDestination()), entity.getBuddiesAnonymized().stream()
						.map(buddyAnonymized -> buddyAnonymized.getId()).collect(Collectors.toSet()));
	}

	public UUID getId()
	{
		return id;
	}

	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return Optional.ofNullable(lastMonitoredActivityDate);
	}

	public Set<GoalDto> getGoals()
	{
		return goals;
	}

	public Set<GoalDto> getGoalsForActivityCategory(ActivityCategory activityCategory)
	{
		return getGoals().stream().filter(g -> g.getActivityCategoryId().equals(activityCategory.getId()))
				.collect(Collectors.toSet());
	}

	public ZoneId getTimeZone()
	{
		return DEFAULT_TIME_ZONE;
	}

	public MessageDestinationDto getAnonymousDestination()
	{
		return anonymousMessageDestination;
	}

	public Set<UUID> getBuddyAnonymizedIds()
	{
		return Collections.unmodifiableSet(buddyAnonymizedIds);
	}

	public Optional<BuddyAnonymized> getBuddyAnonymized(UUID fromUserAnonymizedId)
	{
		return buddyAnonymizedIds.stream().map(id -> BuddyAnonymized.getRepository().findOne(id))
				.filter(ba -> ba.getUserAnonymizedId().filter(id -> id.equals(fromUserAnonymizedId)).isPresent()).findAny();
	}

	public Optional<LocalDateTime> getOldestGoalCreationTime()
	{
		return getGoals().stream().map(goal -> getOldestVersionOfGoal(goal).getCreationTime()).filter(Optional::isPresent)
				.map(Optional::get).min(LocalDateTime::compareTo);
	}

	private GoalDto getOldestVersionOfGoal(GoalDto goal)
	{
		return goal;
	}

	static Set<GoalDto> getGoalsIncludingHistoryItems(UserAnonymized userAnonymizedEntity)
	{
		Set<Goal> activeGoals = userAnonymizedEntity.getGoals();
		Set<Goal> historyItems = getGoalHistoryItems(activeGoals);
		Set<Goal> allGoals = new HashSet<>(activeGoals);
		allGoals.addAll(historyItems);
		return allGoals.stream().map(g -> GoalDto.createInstance(g)).collect(Collectors.toSet());
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

	public boolean hasAnyBuddies()
	{
		return !buddyAnonymizedIds.isEmpty();
	}
}
