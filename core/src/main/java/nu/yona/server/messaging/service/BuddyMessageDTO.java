/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("buddyMessage")
public abstract class BuddyMessageDTO extends MessageDTO
{
	private final Optional<UserDTO> senderUser;
	private final String message;

	protected BuddyMessageDTO(UUID id, SenderInfo senderInfo, ZonedDateTime creationTime, boolean isRead,
			Optional<UserDTO> senderUser, String message)
	{
		super(id, senderInfo, creationTime, isRead);
		this.senderUser = senderUser;
		this.message = message;
	}

	protected BuddyMessageDTO(UUID id, SenderInfo senderInfo, ZonedDateTime creationTime, boolean isRead, UUID relatedMessageID,
			Optional<UserDTO> senderUser, String message)
	{
		super(id, senderInfo, creationTime, isRead, relatedMessageID);
		this.senderUser = senderUser;
		this.message = message;
	}

	@JsonIgnore
	public Optional<UserDTO> getUser()
	{
		return senderUser;
	}

	public String getMessage()
	{
		return message;
	}

	@Component
	public static abstract class Manager extends MessageDTO.Manager
	{
		@Override
		protected SenderInfo getSenderInfoExtensionPoint(Message messageEntity)
		{
			// The buddy entity does not contain the user anonymized ID yet
			BuddyMessage responseMmessageEntity = (BuddyMessage) messageEntity;
			return SenderInfo.createInstanceBuddyDetached(responseMmessageEntity.getSenderUserID(),
					responseMmessageEntity.getSenderNickname());
		}
	}
}
