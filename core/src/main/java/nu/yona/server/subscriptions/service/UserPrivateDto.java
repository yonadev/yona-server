/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.goals.service.GoalDto;

@JsonRootName("userPrivate")
public class UserPrivateDto
{
	private final Optional<LocalDate> lastMonitoredActivityDate;
	private final String yonaPassword;
	private final String nickname;
	private final Set<GoalDto> goals;
	private final VPNProfileDto vpnProfile;
	private final UUID userAnonymizedId;
	private final UUID namedMessageSourceId;
	private final UUID namedMessageDestinationId;
	private final UUID anonymousMessageSourceId;
	private final Set<UUID> buddyIds;
	private final Function<Set<UUID>, Set<BuddyDto>> buddyIdToDtoMapper;

	@JsonCreator
	public UserPrivateDto(@JsonProperty("nickname") String nickname)
	{
		this(Optional.empty(), null, nickname, null, null, null, Collections.emptySet(), Collections.emptySet(), null, null,
				new VPNProfileDto(null));
	}

	UserPrivateDto(Optional<LocalDate> lastMonitoredActivityDate, String yonaPassword, String nickname, UUID namedMessageSourceId,
			UUID namedMessageDestinationId, UUID anonymousMessageSourceId, Set<GoalDto> goals, Set<UUID> buddyIds,
			Function<Set<UUID>, Set<BuddyDto>> buddyIdToDtoMapper, UUID userAnonymizedId, VPNProfileDto vpnProfile)
	{
		Objects.requireNonNull(goals);
		Objects.requireNonNull(buddyIds);
		this.lastMonitoredActivityDate = lastMonitoredActivityDate;
		this.yonaPassword = yonaPassword;
		this.nickname = nickname;
		this.namedMessageSourceId = namedMessageSourceId;
		this.namedMessageDestinationId = namedMessageDestinationId;
		this.anonymousMessageSourceId = anonymousMessageSourceId;
		this.goals = goals;
		this.buddyIds = buddyIds;
		this.buddyIdToDtoMapper = buddyIdToDtoMapper;
		this.userAnonymizedId = userAnonymizedId;
		this.vpnProfile = vpnProfile;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	@JsonInclude(Include.NON_EMPTY)
	public Optional<LocalDate> getLastMonitoredActivityDate()
	{
		return lastMonitoredActivityDate;
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
