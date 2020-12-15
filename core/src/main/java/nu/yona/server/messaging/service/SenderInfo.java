/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import nu.yona.server.Translator;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.BuddyUserPrivateDataDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserPrivateDataBaseDto;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Sender info of the message. The class contents can only be determined when the user reads incoming messages, because buddy IDs
 * are only known to the user, and for the analysis service sending goal conflicts the user ID and nickname are not known.
 */
public final class SenderInfo
{
	private final Optional<UserDto> user;
	private final String nickname;
	private final Optional<UUID> userPhotoId;
	private final boolean isBuddy;
	private final Optional<BuddyDto> buddy;

	private SenderInfo(Optional<UserDto> user, String nickname, Optional<UUID> userPhotoId, boolean isBuddy,
			Optional<BuddyDto> buddy)
	{
		this.user = Objects.requireNonNull(user);
		this.nickname = nickname;
		this.userPhotoId = Objects.requireNonNull(userPhotoId);
		this.isBuddy = isBuddy;
		this.buddy = Objects.requireNonNull(buddy);
	}

	public String getNickname()
	{
		return nickname;
	}

	public Optional<UUID> getUserPhotoId()
	{
		return userPhotoId;
	}

	public boolean isBuddy()
	{
		return isBuddy;
	}

	public Optional<UserDto> getUser()
	{
		return user;
	}

	public Optional<BuddyDto> getBuddy()
	{
		return buddy;
	}

	@Component
	public static class Factory
	{
		@Autowired
		private BuddyService buddyService;

		@Autowired
		private UserService userService;

		@Autowired
		private Translator translator;

		public SenderInfo createInstanceForBuddy(UUID userId, String nickname, Optional<UUID> userPhoto, UUID buddyId)
		{
			return new SenderInfo(Optional.of(userService.getUserWithoutPrivateData(userId)), nickname, userPhoto, true,
					Optional.of(buddyService.getBuddy(buddyId)));
		}

		public SenderInfo createInstanceForDetachedBuddy(Optional<UserDto> user, BuddyUserPrivateDataDto buddyData)
		{
			return user.map(this::createInstanceForDetachedBuddy).orElseGet(() -> createInstanceForDetachedBuddy(buddyData));
		}

		private SenderInfo createInstanceForDetachedBuddy(UserDto user)
		{
			UserPrivateDataBaseDto buddyPrivateData = user.getBuddyPrivateData();
			return new SenderInfo(Optional.of(user), buddyPrivateData.getNickname(), buddyPrivateData.getUserPhotoId(), true,
					Optional.empty());
		}

		private SenderInfo createInstanceForDetachedBuddy(BuddyUserPrivateDataDto buddyData)
		{
			return new SenderInfo(Optional.empty(), buddyData.getNickname(), buddyData.getUserPhotoId(), true, Optional.empty());
		}

		public SenderInfo createInstanceForSelf(UUID userId, String nickname, Optional<UUID> userPhoto)
		{
			String selfNickname = translator.getLocalizedMessage("message.self.nickname", nickname);
			return new SenderInfo(Optional.of(userService.getUser(userId)), selfNickname, userPhoto, false, Optional.empty());
		}

		public SenderInfo createInstanceForSystem()
		{
			String systemNickname = translator.getLocalizedMessage("message.system.nickname");
			return new SenderInfo(Optional.empty(), systemNickname, Optional.empty(), false, Optional.empty());
		}
	}
}
