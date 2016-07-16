/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.service.GoalDTO;

@JsonRootName("userPrivate")
public class UserPrivateDTO
{
	private final String nickname;
	private final Set<GoalDTO> goals;
	private final VPNProfileDTO vpnProfile;
	private final UUID userAnonymizedID;
	private final UUID namedMessageSourceID;
	private final UUID namedMessageDestinationID;
	private final UUID anonymousMessageSourceID;
	private final UUID anonymousMessageDestinationID;
	private final Set<UUID> buddyIDs;
	private final Function<Set<UUID>, Set<BuddyDTO>> buddyIDToDTOMapper;

	@JsonCreator
	public UserPrivateDTO(@JsonProperty("nickname") String nickname)
	{
		this(nickname, null, null, null, null, Collections.emptySet(), Collections.emptySet(), null, null,
				new VPNProfileDTO(null));
	}

	UserPrivateDTO(String nickname, UUID namedMessageSourceID, UUID namedMessageDestinationID, UUID anonymousMessageSourceID,
			UUID anonymousMessageDestinationID, Set<GoalDTO> goals, Set<UUID> buddyIDs,
			Function<Set<UUID>, Set<BuddyDTO>> buddyIDToDTOMapper, UUID userAnonymizedID, VPNProfileDTO vpnProfile)
	{
		Objects.requireNonNull(goals);
		Objects.requireNonNull(buddyIDs);
		this.nickname = nickname;
		this.namedMessageSourceID = namedMessageSourceID;
		this.namedMessageDestinationID = namedMessageDestinationID;
		this.anonymousMessageSourceID = anonymousMessageSourceID;
		this.anonymousMessageDestinationID = anonymousMessageDestinationID;
		this.goals = goals;
		this.buddyIDs = buddyIDs;
		this.buddyIDToDTOMapper = buddyIDToDTOMapper;
		this.userAnonymizedID = userAnonymizedID;
		this.vpnProfile = vpnProfile;
	}

	public String getNickname()
	{
		return nickname;
	}

	@JsonIgnore
	public Set<GoalDTO> getGoals()
	{
		return Collections.unmodifiableSet(goals);
	}

	@JsonIgnore
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

	@JsonIgnore
	public Set<BuddyDTO> getBuddies()
	{
		return buddyIDToDTOMapper.apply(buddyIDs);
	}

	@JsonIgnore
	public UUID getUserAnonymizedID()
	{
		return userAnonymizedID;
	}
}
