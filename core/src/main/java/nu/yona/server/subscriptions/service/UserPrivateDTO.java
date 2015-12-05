/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static java.util.stream.Collectors.toSet;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.goals.service.GoalNotFoundException;

@JsonRootName("userPrivate")
public class UserPrivateDTO
{
	private final String nickname;
	private final Set<String> deviceNames;
	private Set<String> goalNames;
	private VPNProfileDTO vpnProfile;
	private UUID namedMessageSourceID;
	private UUID namedMessageDestinationID;
	private UUID anonymousMessageSourceID;
	private UUID anonymousMessageDestinationID;
	private Set<UUID> buddyIDs;
	private Set<BuddyDTO> buddies;

	@JsonCreator
	public UserPrivateDTO(@JsonProperty("nickname") String nickname,
			@JsonProperty("devices") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> deviceNames,
			@JsonProperty("goals") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> goalNames)
	{
		this(nickname, null, null, null, null, deviceNames, goalNames, null, new VPNProfileDTO(null));
	}

	UserPrivateDTO(String nickname, UUID namedMessageSourceID, UUID namedMessageDestinationID, UUID anonymousMessageSourceID,
			UUID anonymousMessageDestinationID, Set<String> deviceNames, Set<String> goalNames, Set<UUID> buddyIDs,
			VPNProfileDTO vpnProfile)
	{
		this.nickname = nickname;
		this.namedMessageSourceID = namedMessageSourceID;
		this.namedMessageDestinationID = namedMessageDestinationID;
		this.anonymousMessageSourceID = anonymousMessageSourceID;
		this.anonymousMessageDestinationID = anonymousMessageDestinationID;
		this.deviceNames = (deviceNames == null) ? Collections.emptySet() : deviceNames;
		this.goalNames = (goalNames == null) ? Collections.emptySet() : goalNames;
		this.buddyIDs = (buddyIDs == null) ? Collections.emptySet() : buddyIDs;
		this.vpnProfile = vpnProfile;

		this.buddies = Collections.emptySet();
	}

	public String getNickname()
	{
		return nickname;
	}

	@JsonProperty("devices")
	public Set<String> getDeviceNames()
	{
		return Collections.unmodifiableSet(deviceNames);
	}

	@JsonProperty("goals")
	public Set<String> getGoalNames()
	{
		return Collections.unmodifiableSet(goalNames);
	}

	public VPNProfileDTO getVpnProfile()
	{
		return vpnProfile;
	}

	Set<Goal> getGoals()
	{
		return goalNames.stream().map(n -> findGoal(n)).collect(toSet());
	}

	private Goal findGoal(String goalName)
	{
		Goal goal = Goal.getRepository().findByName(goalName);
		if (goal == null)
		{
			throw GoalNotFoundException.notFoundByName(goalName);
		}

		return goal;
	}

	@JsonIgnore
	public UUID getNamedMessageSourceID()
	{
		return namedMessageSourceID;
	}

	@JsonIgnore
	public UUID getNamedMessageDestinationID()
	{
		return namedMessageDestinationID;
	}

	@JsonIgnore
	public UUID getAnonymousMessageSourceID()
	{
		return anonymousMessageSourceID;
	}

	@JsonIgnore
	public UUID getAnonymousMessageDestinationID()
	{
		return anonymousMessageDestinationID;
	}

	@JsonIgnore
	public Set<UUID> getBuddyIDs()
	{
		return Collections.unmodifiableSet(buddyIDs);
	}

	public void setBuddies(Set<BuddyDTO> buddies)
	{
		this.buddies = buddies;
	}

	@JsonIgnore
	public Set<BuddyDTO> getBuddies()
	{
		return Collections.unmodifiableSet(buddies);
	}
}
