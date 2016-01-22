/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.service.MessageDTO;

@JsonRootName("buddyMessage")
public abstract class BuddyMessageDTO extends MessageDTO
{
	private UserDTO user;
	private final String nickname;
	private final String message;

	protected BuddyMessageDTO(UUID id, Date creationTime, UserDTO user, String nickname, String message)
	{
		super(id, creationTime);
		this.user = user;
		this.nickname = nickname;
		this.message = message;
	}

	public UserDTO getUser()
	{
		return user;
	}

	public String getNickname()
	{
		return nickname;
	}

	public String getMessage()
	{
		return message;
	}
}
