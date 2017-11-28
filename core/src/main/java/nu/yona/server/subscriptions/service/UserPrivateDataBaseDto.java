/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;

public class UserPrivateDataBaseDto
{
	private final String nickname;
	protected final Optional<UUID> userPhotoId;
	private final Optional<Set<GoalDto>> goals;
	private final Optional<Set<DeviceBaseDto>> devices;

	protected UserPrivateDataBaseDto(String nickname, Optional<UUID> userPhotoId, Optional<Set<GoalDto>> goals,
			Optional<Set<DeviceBaseDto>> devices)
	{
		this.nickname = nickname;
		this.userPhotoId = userPhotoId;
		this.goals = goals;
		this.devices = devices;
	}

	public String getNickname()
	{
		return nickname;
	}

	@JsonIgnore
	public Optional<Set<GoalDto>> getGoals()
	{
		return goals;
	}

	@JsonIgnore
	public Optional<Set<DeviceBaseDto>> getDevices()
	{
		return devices;
	}

	@JsonIgnore
	public Optional<UUID> getUserPhotoId()
	{
		return userPhotoId;
	}

}
