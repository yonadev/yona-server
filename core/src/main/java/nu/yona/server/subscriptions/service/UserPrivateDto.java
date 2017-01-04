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

import nu.yona.server.goals.service.GoalDto;

@JsonRootName("userPrivate")
public class UserPrivateDto
{
	private final String yonaPassword;
	private final String nickname;
	private final Set<GoalDto> goals;
	private final VPNProfileDto vpnProfile;
	private final UUID userAnonymizedId;
	private final UUID namedMessageSourceId;
	private final UUID namedMessageDestinationId;
	private final UUID anonymousMessageSourceId;
	private final UUID anonymousMessageDestinationId;
	private final Set<UUID> buddyIds;
	private final Function<Set<UUID>, Set<BuddyDto>> buddyIdToDtoMapper;

	@JsonCreator
	public UserPrivateDto(@JsonProperty("nickname") String nickname)
	{
		this(null, nickname, null, null, null, null, Collections.emptySet(), Collections.emptySet(), null, null,
				new VPNProfileDto(null));
	}

	UserPrivateDto(String yonaPassword, String nickname, UUID namedMessageSourceId, UUID namedMessageDestinationId,
			UUID anonymousMessageSourceId, UUID anonymousMessageDestinationId, Set<GoalDto> goals, Set<UUID> buddyIds,
			Function<Set<UUID>, Set<BuddyDto>> buddyIdToDtoMapper, UUID userAnonymizedId, VPNProfileDto vpnProfile)
	{
		Objects.requireNonNull(goals);
		Objects.requireNonNull(buddyIds);
		this.yonaPassword = yonaPassword;
		this.nickname = nickname;
		this.namedMessageSourceId = namedMessageSourceId;
		this.namedMessageDestinationId = namedMessageDestinationId;
		this.anonymousMessageSourceId = anonymousMessageSourceId;
		this.anonymousMessageDestinationId = anonymousMessageDestinationId;
		this.goals = goals;
		this.buddyIds = buddyIds;
		this.buddyIdToDtoMapper = buddyIdToDtoMapper;
		this.userAnonymizedId = userAnonymizedId;
		this.vpnProfile = vpnProfile;
	}

	public String getYonaPassword()
	{
		return yonaPassword;
	}

	public String getNickname()
	{
		return nickname;
	}

	@JsonIgnore
	public Set<GoalDto> getGoals()
	{
		return Collections.unmodifiableSet(goals);
	}

	@JsonIgnore
	public VPNProfileDto getVpnProfile()
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
	public Set<BuddyDto> getBuddies()
	{
		return buddyIdToDtoMapper.apply(buddyIds);
	}

	@JsonIgnore
	public UUID getUserAnonymizedId()
	{
		return userAnonymizedId;
	}
}
