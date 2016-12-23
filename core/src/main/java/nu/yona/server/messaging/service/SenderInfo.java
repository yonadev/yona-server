/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import nu.yona.server.Translator;
import nu.yona.server.subscriptions.service.BuddyDto;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;

/*
 * Sender info of the message. The class contents can only be determined when the user reads incoming messages, because buddy IDs
 * are only known to the user, and for the analysis service sending goal conflicts the user ID and nickname are not known.
 */
public final class SenderInfo
{
	private final Optional<UserDto> user;
	private final String nickname;
	private final boolean isBuddy;
	private final Optional<BuddyDto> buddy;

	private SenderInfo(Optional<UserDto> user, String nickname, boolean isBuddy, Optional<BuddyDto> buddy)
	{
		this.user = user;
		this.nickname = nickname;
		this.isBuddy = isBuddy;
		this.buddy = buddy;
	}

	public String getNickname()
	{
		return nickname;
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

		public SenderInfo createInstanceForBuddy(UUID userId, String nickname, UUID buddyId)
		{
			return new SenderInfo(Optional.of(userService.getPublicUser(userId)), nickname, true,
					Optional.of(buddyService.getBuddy(buddyId)));
		}

		public SenderInfo createInstanceForDetachedBuddy(Optional<UserDto> user, String nickname)
		{
			return new SenderInfo(user, nickname, true, Optional.empty());
		}

		public SenderInfo createInstanceForSelf(UUID userId, String nickname)
		{
			String selfNickname = translator.getLocalizedMessage("message.self.nickname", nickname);
			return new SenderInfo(Optional.of(userService.getPrivateUser(userId)), selfNickname, false, Optional.empty());
		}
	}
}
