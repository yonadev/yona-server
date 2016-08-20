/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.UserAnonymized;

public class UserAnonymizedDTO
{
	private final UUID id;
	private final Set<GoalDTO> goals;
	private final MessageDestinationDTO anonymousMessageDestination;
	private final Set<UUID> buddyAnonymizedIDs;

	public UserAnonymizedDTO(UUID id, Set<GoalDTO> goals, MessageDestinationDTO anonymousMessageDestination,
			Set<UUID> buddyAnonymizedIDs)
	{
		this.id = id;
		this.goals = new HashSet<>(goals);
		this.anonymousMessageDestination = anonymousMessageDestination;
		this.buddyAnonymizedIDs = buddyAnonymizedIDs;
	}

	public static UserAnonymizedDTO createInstance(UserAnonymized entity)
	{
		return new UserAnonymizedDTO(entity.getID(), getGoalsIncludingHistoryItems(entity),
				MessageDestinationDTO.createInstance(entity.getAnonymousDestination()), entity.getBuddiesAnonymized().stream()
						.map(buddyAnonymized -> buddyAnonymized.getID()).collect(Collectors.toSet()));
	}

	public UUID getID()
	{
		return id;
	}

	public Set<GoalDTO> getGoals()
	{
		return goals;
	}

	public String getTimeZoneId()
	{
		return "Europe/Amsterdam";
	}

	public MessageDestinationDTO getAnonymousDestination()
	{
		return anonymousMessageDestination;
	}

	public Set<MessageDestinationDTO> getBuddyDestinations()
	{
		return buddyAnonymizedIDs.stream().map(id -> BuddyAnonymized.getRepository().findOne(id))
				.filter(ba -> ba.getSendingStatus() == Status.ACCEPTED)
				.map(ba -> MessageDestinationDTO.createInstance(ba.getUserAnonymized().getAnonymousDestination()))
				.collect(Collectors.toSet());
	}

	public Optional<BuddyAnonymized> getBuddyAnonymized(UUID fromUserAnonymizedID)
	{
		return buddyAnonymizedIDs.stream().map(id -> BuddyAnonymized.getRepository().findOne(id))
				.filter(ba -> ba.getUserAnonymizedID().filter(id -> id.equals(fromUserAnonymizedID)).isPresent()).findAny();
	}

	public Optional<ZonedDateTime> getOldestGoalCreationTime()
	{
		return getGoals().stream().map(goal -> getOldestVersionOfGoal(goal).getCreationTime()).filter(Optional::isPresent)
				.map(Optional::get).min(ZonedDateTime::compareTo);
	}

	private GoalDTO getOldestVersionOfGoal(GoalDTO goal)
	{
		return goal;
	}

	static Set<GoalDTO> getGoalsIncludingHistoryItems(UserAnonymized userAnonymizedEntity)
	{
		Set<Goal> activeGoals = userAnonymizedEntity.getGoals();
		Set<Goal> historyItems = getGoalHistoryItems(activeGoals);
		Set<Goal> allGoals = new HashSet<>(activeGoals);
		allGoals.addAll(historyItems);
		return allGoals.stream().map(g -> GoalDTO.createInstance(g)).collect(Collectors.toSet());
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
		return !buddyAnonymizedIDs.isEmpty();
	}
}
