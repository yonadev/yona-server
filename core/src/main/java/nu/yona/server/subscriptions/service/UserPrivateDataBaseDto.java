/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;

public class UserPrivateDataBaseDto
{
	private final String firstName;
	private final String lastName;
	private final String nickname;
	protected final Optional<UUID> userPhotoId;
	private final Optional<Set<GoalDto>> goals;
	private final Optional<Set<DeviceBaseDto>> devices;

	protected UserPrivateDataBaseDto(String firstName, String lastName, String nickname, Optional<UUID> userPhotoId,
			Optional<Set<GoalDto>> goals, Optional<Set<DeviceBaseDto>> devices)
	{
		this.firstName = firstName;
		this.lastName = lastName;
		this.nickname = nickname;
		this.userPhotoId = Objects.requireNonNull(userPhotoId);
		this.goals = Objects.requireNonNull(goals);
		this.devices = Objects.requireNonNull(devices);
	}

	public String getFirstName()
	{
		return firstName;
	}

	public String getLastName()
	{
		return lastName;
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
