/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.service.GoalDTO;

public class GoalIdMapping
{
	private final UserDTO user;
	private final Set<UUID> userGoalIds;
	private final Map<UUID, UUID> goalIdToBuddyIdmapping;

	private GoalIdMapping(UserDTO user, Set<UUID> userGoalIds, Map<UUID, UUID> goalIdToBuddyIdmapping)
	{
		this.user = user;
		this.userGoalIds = userGoalIds;
		this.goalIdToBuddyIdmapping = goalIdToBuddyIdmapping;
	}

	public UserDTO getUser()
	{
		return user;
	}

	public UUID getUserId()
	{
		return user.getId();
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

	public static GoalIdMapping createInstance(UserDTO user)
	{
		Set<UUID> userGoalIds = user.getPrivateData().getGoals().stream().map(GoalDTO::getGoalId).collect(Collectors.toSet());
		Map<UUID, UUID> goalIdToBuddyIdmapping = new HashMap<>();
		user.getPrivateData().getBuddies()
				.forEach(b -> b.getGoals().forEach(g -> goalIdToBuddyIdmapping.put(g.getGoalId(), b.getId())));
		return new GoalIdMapping(user, userGoalIds, goalIdToBuddyIdmapping);
	}
}
