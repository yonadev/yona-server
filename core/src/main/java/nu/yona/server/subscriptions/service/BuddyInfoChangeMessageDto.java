/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDto;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.BuddyInfoChangeMessage;

@JsonRootName("buddyInfoChangeMessage")
public class BuddyInfoChangeMessageDto extends BuddyMessageLinkedUserDto
{
	private static final String PROCESS = "process";
	private final boolean isProcessed;

	private BuddyInfoChangeMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, String message,
			boolean isProcessed)
	{
		super(id, creationTime, isRead, senderInfo, message);
		this.isProcessed = isProcessed;
	}

	@Override
	public String getType()
	{
		return "BuddyInfoChangeMessage";
	}

	@JsonIgnore
	public boolean isProcessed()
	{
		return isProcessed;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
		if (!isProcessed)
		{
			possibleActions.add(PROCESS);
		}
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return this.isProcessed;
	}

	public static BuddyInfoChangeMessageDto createInstance(BuddyInfoChangeMessage messageEntity, SenderInfo senderInfo)
	{
		return new BuddyInfoChangeMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.isProcessed());
	}

	@Component
	private static class Manager extends BuddyMessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(BuddyInfoChangeMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return BuddyInfoChangeMessageDto.createInstance((BuddyInfoChangeMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				case PROCESS:
					return handleAction_Process(actingUser, (BuddyInfoChangeMessage) messageEntity);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDto handleAction_Process(UserDto actingUser, BuddyInfoChangeMessage messageEntity)
		{
			if (messageEntity.getRelatedUserAnonymizedId().isPresent())
			{
				buddyService.updateBuddyUserInfo(actingUser.getId(), messageEntity.getRelatedUserAnonymizedId().get(),
						messageEntity.getNewNickname());
			}

			messageEntity = updateMessageStatusAsProcessed(messageEntity);

			return MessageActionDto.createInstanceActionDone(theDtoFactory.createInstance(actingUser, messageEntity));
		}

		private BuddyInfoChangeMessage updateMessageStatusAsProcessed(BuddyInfoChangeMessage messageEntity)
		{
			messageEntity.setProcessed();
			return Message.getRepository().save(messageEntity);
		}
	}
}
