/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;

public class BuddyUserPrivateDataDto extends UserPrivateDataBaseDto
{
	BuddyUserPrivateDataDto(String nickname, Set<GoalDto> goals, Set<DeviceBaseDto> devices)
	{
		super(nickname, goals, devices);
	}

	static BuddyUserPrivateDataDto createInstance(Buddy buddyEntity)
	{
		Objects.requireNonNull(buddyEntity, "buddyEntity cannot be null");

		Set<GoalDto> goals;
		Set<DeviceBaseDto> devices;
		if (buddyEntity.getSendingStatus() == Status.ACCEPTED)
		{
			goals = UserAnonymizedDto.getGoalsIncludingHistoryItems(buddyEntity.getBuddyAnonymized().getUserAnonymized());
			devices = null; // TODO buddyEntity.getDevices();
		}
		else
		{
			goals = Collections.emptySet();
			devices = Collections.emptySet();
		}
		return new BuddyUserPrivateDataDto(buddyEntity.getNickname(), goals, devices);
	}
}
