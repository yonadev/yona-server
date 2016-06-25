package nu.yona.server.subscriptions.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.goals.service.GoalDTO;

public class GoalIDMapping
{
	private final UserDTO user;
	private final Set<UUID> userGoalIDs;
	private final Map<UUID, UUID> goalIDToBuddyIDmapping;

	private GoalIDMapping(UserDTO user, Set<UUID> userGoalIDs, Map<UUID, UUID> goalIDToBuddyIDmapping)
	{
		this.user = user;
		this.userGoalIDs = userGoalIDs;
		this.goalIDToBuddyIDmapping = goalIDToBuddyIDmapping;
	}

	public UserDTO getUser()
	{
		return user;
	}

	public UUID getUserID()
	{
		return user.getID();
	}

	public boolean isUserGoal(UUID goalID)
	{
		return userGoalIDs.contains(goalID);
	}

	public UUID getBuddyID(UUID goalID)
	{
		UUID uuid = goalIDToBuddyIDmapping.get(goalID);
		if (uuid == null)
		{
			throw new IllegalArgumentException("Goal " + goalID + " not found");
		}
		return uuid;
	}

	public static GoalIDMapping createInstance(UserDTO user)
	{
		UUID userID = user.getID();
		Set<UUID> userGoalIDs = user.getPrivateData().getGoals().stream().map(GoalDTO::getID).collect(Collectors.toSet());
		Map<UUID, UUID> goalIDToBuddyIDmapping = new HashMap<>();
		user.getPrivateData().getBuddies()
				.forEach(b -> b.getGoals().forEach(g -> goalIDToBuddyIDmapping.put(g.getID(), b.getID())));
		return new GoalIDMapping(user, userGoalIDs, goalIDToBuddyIDmapping);
	}
}