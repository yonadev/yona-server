/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("buddyMessage")
public abstract class BuddyMessageDTO extends MessageDTO
{
	private UserDTO senderUser;
	private final String senderNickname;
	private final String message;

	protected BuddyMessageDTO(UUID id, ZonedDateTime creationTime, boolean isRead, UserDTO senderUser, String senderNickname, String message)
	{
		super(id, creationTime, isRead);
		this.senderUser = senderUser;
		this.senderNickname = senderNickname;
		this.message = message;
	}

	protected BuddyMessageDTO(UUID id, ZonedDateTime creationTime, boolean isRead, UUID relatedMessageID, UserDTO senderUser, String senderNickname,
			String message)
	{
		super(id, creationTime, isRead, relatedMessageID);
		this.senderUser = senderUser;
		this.senderNickname = senderNickname;
		this.message = message;
	}

	@JsonIgnore
	public UserDTO getUser()
	{
		return senderUser;
	}

	public String getNickname()
	{
		return senderNickname;
	}

	public String getMessage()
	{
		return message;
	}
}
