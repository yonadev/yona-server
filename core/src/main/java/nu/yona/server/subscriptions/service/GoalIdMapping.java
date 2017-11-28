/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.service.GoalDto;

public class GoalIdMapping
{
	private final UserDto user;
	private final Set<UUID> userGoalIds;
	private final Map<UUID, UUID> goalIdToBuddyIdmapping;

	private GoalIdMapping(UserDto user, Set<UUID> userGoalIds, Map<UUID, UUID> goalIdToBuddyIdmapping)
	{
		this.user = user;
		this.userGoalIds = userGoalIds;
		this.goalIdToBuddyIdmapping = goalIdToBuddyIdmapping;
	}

	public UserDto getUser()
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

	public static GoalIdMapping createInstance(UserDto user)
	{
		Set<UUID> userGoalIds = user.getOwnPrivateData().getGoals().orElse(Collections.emptySet()).stream()
				.map(GoalDto::getGoalId).collect(Collectors.toSet());
		Map<UUID, UUID> goalIdToBuddyIdmapping = new HashMap<>();
		user.getOwnPrivateData().getBuddies().forEach(b -> b.getGoals().orElse(Collections.emptySet())
				.forEach(g -> goalIdToBuddyIdmapping.put(g.getGoalId(), b.getId())));
		return new GoalIdMapping(user, userGoalIds, goalIdToBuddyIdmapping);
	}
}
