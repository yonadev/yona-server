/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nu.yona.server.device.service.BuddyDeviceDto;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

public class BuddyUserPrivateDataDto extends UserPrivateDataBaseDto
{
	BuddyUserPrivateDataDto(String nickname, Optional<UUID> userPhotoId, Set<GoalDto> goals, Set<DeviceBaseDto> devices)
	{
		super(nickname, userPhotoId, goals, devices);
	}

	static BuddyUserPrivateDataDto createInstance(Buddy buddyEntity)
	{
		Objects.requireNonNull(buddyEntity, "buddyEntity cannot be null");

		Set<GoalDto> goals;
		Set<DeviceBaseDto> devices;
		if (buddyEntity.getSendingStatus() == Status.ACCEPTED)
		{
			goals = UserAnonymizedDto.getGoalsIncludingHistoryItems(buddyEntity.getBuddyAnonymized().getUserAnonymized());
			devices = buddyEntity.getDevices().stream().map(BuddyDeviceDto::createInstance).collect(Collectors.toSet());
		}
		else
		{
			goals = null;
			devices = null;
		}
		return new BuddyUserPrivateDataDto(buddyEntity.getNickname(), buddyEntity.getUserPhotoId(), goals, devices);
	}
}
