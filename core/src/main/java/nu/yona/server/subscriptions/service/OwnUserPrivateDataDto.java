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
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.Constants;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;

public class OwnUserPrivateDataDto extends UserPrivateDataBaseDto
{
	private final Optional<LocalDate> lastMonitoredActivityDate;
	private final String yonaPassword;
	private final VPNProfileDto vpnProfile;
	private final UUID userAnonymizedId;
	private final UUID namedMessageSourceId;
	private final UUID anonymousMessageSourceId;
	private final Set<BuddyDto> buddies;

	@JsonCreator
	public OwnUserPrivateDataDto(@JsonProperty("nickname") String nickname)
	{
		this(Optional.empty(), null, nickname, null, null, Collections.emptySet(), Collections.emptySet(), null,
				new VPNProfileDto(null), Collections.emptySet());
	}

	OwnUserPrivateDataDto(Optional<LocalDate> lastMonitoredActivityDate, String yonaPassword, String nickname,
			UUID namedMessageSourceId, UUID anonymousMessageSourceId, Set<GoalDto> goals, Set<BuddyDto> buddies,
			UUID userAnonymizedId, VPNProfileDto vpnProfile, Set<DeviceBaseDto> devices)
	{
		super(nickname, goals, devices);
		Objects.requireNonNull(goals);
		Objects.requireNonNull(buddies);
		this.lastMonitoredActivityDate = lastMonitoredActivityDate;
		this.yonaPassword = yonaPassword;
		this.namedMessageSourceId = namedMessageSourceId;
		this.anonymousMessageSourceId = anonymousMessageSourceId;
		this.buddies = buddies;
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
	public UUID getAnonymousMessageSourceId()
	{
		return anonymousMessageSourceId;
	}

	@JsonIgnore
	public Set<UUID> getBuddyIds()
	{
		return buddies.stream().map(BuddyDto::getId).collect(Collectors.toSet());
	}

	@JsonIgnore
	public Set<BuddyDto> getBuddies()
	{
		return Collections.unmodifiableSet(buddies);
	}

	@JsonIgnore
	public UUID getUserAnonymizedId()
	{
		return userAnonymizedId;
	}
}
