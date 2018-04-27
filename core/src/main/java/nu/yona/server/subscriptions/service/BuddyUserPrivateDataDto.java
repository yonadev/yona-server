/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;

import nu.yona.server.device.service.BuddyDeviceDto;
import nu.yona.server.device.service.DeviceBaseDto;
import nu.yona.server.goals.service.GoalDto;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.migration.EncryptFirstAndLastName;

public class BuddyUserPrivateDataDto extends UserPrivateDataBaseDto
{
	private static final int VERSION_OF_NAME_MIGRATION = EncryptFirstAndLastName.class.getAnnotation(Order.class).value();

	BuddyUserPrivateDataDto(String firstName, String lastName, String nickname, Optional<UUID> userPhotoId)
	{
		super(firstName, lastName, nickname, userPhotoId, Optional.empty(), Optional.empty());
	}

	BuddyUserPrivateDataDto(String firstName, String lastName, String nickname, Optional<UUID> userPhotoId, Set<GoalDto> goals,
			Set<DeviceBaseDto> devices)
	{
		super(firstName, lastName, nickname, userPhotoId, Optional.of(goals), Optional.of(devices));
	}

	static BuddyUserPrivateDataDto createInstance(Buddy buddyEntity)
	{
		Objects.requireNonNull(buddyEntity, "buddyEntity cannot be null");

		if (buddyEntity.getSendingStatus() == Status.ACCEPTED)
		{
			Set<GoalDto> goals = UserAnonymizedDto
					.getGoalsIncludingHistoryItems(buddyEntity.getBuddyAnonymized().getUserAnonymized());
			Set<DeviceBaseDto> devices = buddyEntity.getDevices().stream().map(BuddyDeviceDto::createInstance)
					.collect(Collectors.toSet());
			User user = buddyEntity.getUser();
			String firstName = determineName(buddyEntity::getFirstName, user, User::getFirstName);
			String lastName = determineName(buddyEntity::getLastName, user, User::getLastName);
			return new BuddyUserPrivateDataDto(firstName, lastName, buddyEntity.getNickname(), buddyEntity.getUserPhotoId(),
					goals, devices);
		}
		return new BuddyUserPrivateDataDto(buddyEntity.getFirstName(), buddyEntity.getLastName(), buddyEntity.getNickname(),
				buddyEntity.getUserPhotoId());
	}

	private static String determineName(Supplier<String> buddyUserNameGetter, User user, Function<User, String> userNameGetter)
	{
		String name = buddyUserNameGetter.get();
		if (name != null)
		{
			return name;
		}
		if ((user != null) && (user.getPrivateDataMigrationVersion() < VERSION_OF_NAME_MIGRATION))
		{
			// User is not deleted yet and not yet migrated, so get the name from the user entity
			name = userNameGetter.apply(user);
		}
		if (name != null)
		{
			return name;
		}
		// We're apparently in a migration process to move first and last name to the private data
		// The app will fetch the message, causing processing of all unprocessed messages. That'll fill in the first and last
		// name in the buddy entity, so from then onward, the user will see the right data
		return "Not available";
	}

	public static BuddyUserPrivateDataDto createInstance(String firstName, String lastName, String nickname,
			Optional<UUID> userPhotoId)
	{
		return new BuddyUserPrivateDataDto(firstName, lastName, nickname, userPhotoId);
	}
}
