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
	private final UUID userAnonymizedId;
	private final UUID namedMessageSourceId;
	private final UUID namedMessageDestinationId;
	private final UUID anonymousMessageSourceId;
	private final UUID anonymousMessageDestinationId;
	private final Set<UUID> buddyIds;
	private final Function<Set<UUID>, Set<BuddyDTO>> buddyIdToDTOMapper;

	@JsonCreator
	public UserPrivateDTO(@JsonProperty("nickname") String nickname)
	{
		this(nickname, null, null, null, null, Collections.emptySet(), Collections.emptySet(), null, null,
				new VPNProfileDTO(null));
	}

	UserPrivateDTO(String nickname, UUID namedMessageSourceId, UUID namedMessageDestinationId, UUID anonymousMessageSourceId,
			UUID anonymousMessageDestinationId, Set<GoalDTO> goals, Set<UUID> buddyIds,
			Function<Set<UUID>, Set<BuddyDTO>> buddyIdToDTOMapper, UUID userAnonymizedId, VPNProfileDTO vpnProfile)
	{
		Objects.requireNonNull(goals);
		Objects.requireNonNull(buddyIds);
		this.nickname = nickname;
		this.namedMessageSourceId = namedMessageSourceId;
		this.namedMessageDestinationId = namedMessageDestinationId;
		this.anonymousMessageSourceId = anonymousMessageSourceId;
		this.anonymousMessageDestinationId = anonymousMessageDestinationId;
		this.goals = goals;
		this.buddyIds = buddyIds;
		this.buddyIdToDTOMapper = buddyIdToDTOMapper;
		this.userAnonymizedId = userAnonymizedId;
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
	public UUID getNamedMessageSourceId()
	{
		return namedMessageSourceId;
	}

	@JsonIgnore
	public UUID getNamedMessageDestinationId()
	{
		return namedMessageDestinationId;
	}

	@JsonIgnore
	public UUID getAnonymousMessageSourceId()
	{
		return anonymousMessageSourceId;
	}

	@JsonIgnore
	public UUID getAnonymousMessageDestinationId()
	{
		return anonymousMessageDestinationId;
	}

	@JsonIgnore
	public Set<UUID> getBuddyIds()
	{
		return Collections.unmodifiableSet(buddyIds);
	}

	@JsonIgnore
	public Set<BuddyDTO> getBuddies()
	{
		return buddyIdToDTOMapper.apply(buddyIds);
	}

	@JsonIgnore
	public UUID getUserAnonymizedId()
	{
		return userAnonymizedId;
	}
}
