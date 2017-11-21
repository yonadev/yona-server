/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;

public class UserPrivateDataBaseDto
{
	private final String nickname;
	private final Set<GoalDto> goals;
	private final Set<DeviceBaseDto> devices;

	protected UserPrivateDataBaseDto(String nickname, Set<GoalDto> goals, Set<DeviceBaseDto> devices)
	{
		this.devices = devices;
		this.nickname = nickname;
		this.goals = Objects.requireNonNull(goals);
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
	public Set<DeviceBaseDto> getDevices()
	{
		return devices;
	}

}
