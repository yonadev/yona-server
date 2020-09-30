/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;

public class GoalIdMapping
{
	private final UUID userId;
	private final Set<UUID> userGoalIds;
	private final Map<UUID, UUID> goalIdToBuddyIdmapping;
	private final Map<UUID, UUID> buddyIdToUserIdmapping;

	private GoalIdMapping(UUID userId, Set<UUID> userGoalIds, Map<UUID, UUID> goalIdToBuddyIdmapping,
			Map<UUID, UUID> buddyIdToUserIdmapping)
	{
		this.userId = userId;
		this.userGoalIds = userGoalIds;
		this.goalIdToBuddyIdmapping = goalIdToBuddyIdmapping;
		this.buddyIdToUserIdmapping = buddyIdToUserIdmapping;
	}

	public UUID getUserId()
	{
		return userId;
	}

	public UUID getBuddyUserId(UUID buddyId)
	{
		UUID uuid = buddyIdToUserIdmapping.get(buddyId);
		if (uuid == null)
		{
			throw new IllegalArgumentException("Buddy " + buddyId + " not found");
		}
		return uuid;
	}

	public boolean isUserGoal(UUID goalId)
	{
		return userGoalIds.contains(goalId);
	}

	public UUID getBuddyId(UUID goalId)
	{
		UUID uuid = goalIdToBuddyIdmapping.get(goalId);
		if (uuid == null)
		{
			throw new IllegalArgumentException("Goal " + goalId + " not found");
		}
		return uuid;
	}

	public static GoalIdMapping createInstance(UserAnonymizedService userAnonymizedService, User user)
	{
		Set<UUID> userGoalIds = userAnonymizedService.getUserAnonymized(user.getUserAnonymizedId()).getGoalsIncludingHistoryItems().stream().map(GoalDto::getGoalId).collect(Collectors.toSet());
		Map<UUID, UUID> goalIdToBuddyIdmapping = new HashMap<>();
		user.getBuddies().stream().filter(Buddy::isAccepted).forEach(b -> userAnonymizedService.getUserAnonymized(b.getUserAnonymizedId().get()).getGoalsIncludingHistoryItems()
				.forEach(g -> goalIdToBuddyIdmapping.put(g.getGoalId(), b.getId())));
		Map<UUID, UUID> buddyIdToUserIdmapping = new HashMap<>();
		user.getBuddies().forEach(b -> buddyIdToUserIdmapping.put(b.getId(), b.getUser().getId()));
		return new GoalIdMapping(user.getId(), userGoalIds, goalIdToBuddyIdmapping, buddyIdToUserIdmapping);
	}
}
