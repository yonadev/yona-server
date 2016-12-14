/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.messaging.entities.Message;

@JsonRootName("buddyMessage")
public abstract class BuddyMessageDTO extends MessageDTO
{
	private final String message;

	protected BuddyMessageDTO(UUID id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, String message)
	{
		super(id, creationTime, isRead, senderInfo);
		this.message = message;
	}

	protected BuddyMessageDTO(UUID id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, UUID relatedMessageId,
			String message)
	{
		super(id, senderInfo, creationTime, isRead, relatedMessageId);
		this.message = message;
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
			BuddyMessage buddyMessageEntity = (BuddyMessage) messageEntity;
			return createSenderInfoForDetachedBuddy(buddyMessageEntity.getSenderUser(), buddyMessageEntity.getSenderNickname());
		}
	}
}
