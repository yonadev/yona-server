/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.service.GoalDTO;

@JsonRootName("userPrivate")
public class UserPrivateDTO
{
	private final String nickname;
	private final Set<String> deviceNames;
	private Set<GoalDTO> goals;
	private VPNProfileDTO vpnProfile;
	private UUID userAnonymizedID;
	private UUID namedMessageSourceID;
	private UUID namedMessageDestinationID;
	private UUID anonymousMessageSourceID;
	private UUID anonymousMessageDestinationID;
	private Set<UUID> buddyIDs;
	private Set<BuddyDTO> buddies;

	@JsonCreator
	public UserPrivateDTO(@JsonProperty("nickname") String nickname,
			@JsonProperty("devices") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> deviceNames)
	{
		this(nickname, null, null, null, null, deviceNames, null, null, null, new VPNProfileDTO(null));
	}

	UserPrivateDTO(String nickname, UUID namedMessageSourceID, UUID namedMessageDestinationID, UUID anonymousMessageSourceID,
			UUID anonymousMessageDestinationID, Set<String> deviceNames, Set<GoalDTO> goals, Set<UUID> buddyIDs,
			UUID userAnonymizedID, VPNProfileDTO vpnProfile)
	{
		this.nickname = nickname;
		this.namedMessageSourceID = namedMessageSourceID;
		this.namedMessageDestinationID = namedMessageDestinationID;
		this.anonymousMessageSourceID = anonymousMessageSourceID;
		this.anonymousMessageDestinationID = anonymousMessageDestinationID;
		this.deviceNames = (deviceNames == null) ? Collections.emptySet() : deviceNames;
		this.goals = (goals == null) ? Collections.emptySet() : goals;
		this.buddyIDs = (buddyIDs == null) ? Collections.emptySet() : buddyIDs;
		this.userAnonymizedID = userAnonymizedID;
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

	@JsonIgnore
	public Set<GoalDTO> getGoals()
	{
		return Collections.unmodifiableSet(goals);
	}

	public VPNProfileDTO getVpnProfile()
	{
		return vpnProfile;
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

	@JsonIgnore
	public UUID getUserAnonymizedID()
	{
		return userAnonymizedID;
	}
}
