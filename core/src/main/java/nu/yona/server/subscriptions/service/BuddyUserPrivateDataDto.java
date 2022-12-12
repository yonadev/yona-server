/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import nu.yona.server.device.service.BuddyDeviceDto;
import nu.yona.server.device.service.DeviceAnonymizedDto;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.User;

public class BuddyUserPrivateDataDto extends UserPrivateDataBaseDto
{
	private final boolean isFetchable;

	BuddyUserPrivateDataDto(String firstName, String lastName, String nickname, Optional<UUID> userPhotoId, boolean isFetchable)
	{
		super(firstName, lastName, nickname, userPhotoId, Optional.empty(), Optional.empty());
		this.isFetchable = isFetchable;
	}

	BuddyUserPrivateDataDto(String firstName, String lastName, String nickname, Optional<UUID> userPhotoId, Set<GoalDto> goals,
			Set<DeviceBaseDto> devices)
	{
		super(firstName, lastName, nickname, userPhotoId, Optional.of(goals), Optional.of(devices));
		this.isFetchable = true;
	}

	static BuddyUserPrivateDataDto createInstance(Buddy buddyEntity, BuddyAnonymizedDto buddyAnonymizedDto,
			UserAnonymizedDto buddyUserAnonymizedDto)
	{
		return createInstance(buddyEntity, buddyAnonymizedDto, Optional.of(buddyUserAnonymizedDto));
	}

	static BuddyUserPrivateDataDto createInstance(Buddy buddyEntity, BuddyAnonymizedDto buddyAnonymizedDto,
			Optional<UserAnonymizedDto> buddyUserAnonymizedDtoOptional)
	{
		Objects.requireNonNull(buddyEntity, "buddyEntity cannot be null");

		if (canIncludePrivateData(buddyAnonymizedDto))
		{
			UserAnonymizedDto buddyUserAnonymizedDto = buddyUserAnonymizedDtoOptional.orElseThrow(
					() -> new IllegalStateException("Should have user anonymized when buddy relationship is established"));
			Set<GoalDto> goals = buddyUserAnonymizedDto.getGoalsIncludingHistoryItems();
			Set<DeviceBaseDto> devices = buddyEntity.getDevices().stream().map(bd -> BuddyDeviceDto.createInstance(bd,
							getDeviceAnonymizedIfExisting(buddyUserAnonymizedDto, bd.getDeviceAnonymizedId())))
					.collect(Collectors.toSet());
			Optional<User> user = Optional.ofNullable(buddyEntity.getUser());
			String firstName = buddyEntity.determineFirstName(user);
			String lastName = buddyEntity.determineLastName(user);
			return new BuddyUserPrivateDataDto(firstName, lastName, buddyEntity.getNickname(), buddyEntity.getUserPhotoId(),
					goals, devices);
		}
		// Pass true for isFetchable. When there is a buddy entity, the related user entity is always fetchable
		return new BuddyUserPrivateDataDto(buddyEntity.getFirstName(), buddyEntity.getLastName(), buddyEntity.getNickname(),
				buddyEntity.getUserPhotoId(), true);
	}

	private static Optional<DeviceAnonymizedDto> getDeviceAnonymizedIfExisting(UserAnonymizedDto buddyUserAnonymizedDto,
			UUID deviceAnonymizedId)
	{
		// We cannot use UserAnonymizedDto.getDeviceAnonymized here because the device anonymized isn't always available
		return buddyUserAnonymizedDto.getDeviceAnonymizedIfExisting(deviceAnonymizedId);
	}

	public static BuddyUserPrivateDataDto createInstance(String firstName, String lastName, String nickname,
			Optional<UUID> userPhotoId, boolean isFetcheable)
	{
		return new BuddyUserPrivateDataDto(firstName, lastName, nickname, userPhotoId, isFetcheable);
	}

	@Override
	@JsonIgnore
	public boolean isFetchable()
	{
		return isFetchable;
	}

	public static boolean canIncludePrivateData(BuddyAnonymizedDto buddyAnonymizedDto)
	{
		return (buddyAnonymizedDto.getReceivingStatus() == Status.ACCEPTED) || (buddyAnonymizedDto.getSendingStatus()
				== Status.ACCEPTED);
	}
}
