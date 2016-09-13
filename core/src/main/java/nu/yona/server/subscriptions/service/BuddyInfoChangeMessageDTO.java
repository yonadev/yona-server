/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDTO;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.BuddyInfoChangeMessage;

@JsonRootName("buddyInfoChangeMessage")
public class BuddyInfoChangeMessageDTO extends BuddyMessageLinkedUserDTO
{
	private static final String PROCESS = "process";
	private final String newNickname;
	private final boolean isProcessed;

	private BuddyInfoChangeMessageDTO(UUID id, ZonedDateTime creationTime, boolean isRead, SenderInfo senderInfo, String message,
			String newNickname, boolean isProcessed)
	{
		super(id, creationTime, isRead, senderInfo, message);
		this.newNickname = newNickname;
		this.isProcessed = isProcessed;
	}

	@Override
	public String getType()
	{
		return "BuddyInfoChangeMessage";
	}

	public String getNewNickname()
	{
		return newNickname;
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

	public static BuddyInfoChangeMessageDTO createInstance(UserDTO actingUser, BuddyInfoChangeMessage messageEntity,
			SenderInfo senderInfo)
	{
		return new BuddyInfoChangeMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.getNewNickname(), messageEntity.isProcessed());
	}

	@Component
	private static class Manager extends BuddyMessageDTO.Manager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(BuddyInfoChangeMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return BuddyInfoChangeMessageDTO.createInstance(actingUser, (BuddyInfoChangeMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case PROCESS:
					return handleAction_Process(actingUser, (BuddyInfoChangeMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDTO handleAction_Process(UserDTO actingUser, BuddyInfoChangeMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			if (messageEntity.getRelatedUserAnonymizedID().isPresent())
			{
				buddyService.updateBuddyInfo(actingUser.getID(), messageEntity.getRelatedUserAnonymizedID().get(),
						messageEntity.getNewNickname());
			}

			messageEntity = updateMessageStatusAsProcessed(messageEntity);

			return MessageActionDTO.createInstanceActionDone(theDTOFactory.createInstance(actingUser, messageEntity));
		}

		private BuddyInfoChangeMessage updateMessageStatusAsProcessed(BuddyInfoChangeMessage messageEntity)
		{
			messageEntity.setProcessed();
			return Message.getRepository().save(messageEntity);
		}
	}
}
