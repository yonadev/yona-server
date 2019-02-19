/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.messaging.entities.BuddyMessage.BuddyInfoParameters;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.subscriptions.service.OwnUserPrivateDataDto;
import nu.yona.server.subscriptions.service.UserDto;

@JsonRootName("buddyMessage")
public abstract class BuddyMessageDto extends MessageDto
{
	private final String message;

	protected BuddyMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, String message)
	{
		super(id, creationTime, isRead, senderInfo);
		this.message = message;
	}

	protected BuddyMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			Optional<Long> relatedMessageId, String message)
	{
		super(id, senderInfo, creationTime, isRead, relatedMessageId);
		this.message = message;
	}

	public String getMessage()
	{
		return message;
	}

	public static BuddyInfoParameters createBuddyInfoParametersInstance(UserDto userWithPrivateData)
	{
		return createBuddyInfoParametersInstance(userWithPrivateData,
				userWithPrivateData.getOwnPrivateData().getUserAnonymizedId());
	}

	public static BuddyInfoParameters createBuddyInfoParametersInstance(UserDto userWithPrivateData, UUID relatedUserAnonymizedId)
	{
		OwnUserPrivateDataDto ownPrivateData = userWithPrivateData.getOwnPrivateData();
		return new BuddyInfoParameters(relatedUserAnonymizedId, userWithPrivateData.getId(), ownPrivateData.getFirstName(),
				ownPrivateData.getLastName(), userWithPrivateData.getPrivateData().getNickname(),
				ownPrivateData.getUserPhotoId());
	}

	@Component
	public abstract static class Manager extends MessageDto.Manager
	{
		@Override
		protected Optional<UUID> getSenderUserAnonymizedId(UserDto actingUser, Message messageEntity)
		{
			BuddyMessage buddyMessageEntity = (BuddyMessage) messageEntity;
			if (actingUser.getId().equals(buddyMessageEntity.getSenderUserId()))
			{
				return Optional.of(actingUser.getOwnPrivateData().getUserAnonymizedId());
			}
			return messageEntity.getRelatedUserAnonymizedId();
		}
	}
}
