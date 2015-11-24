/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;

@JsonRootName("buddyConnectResponseMessage")
public class BuddyConnectResponseMessageDTO extends BuddyConnectMessageDTO
{
	private static final String PROCESS = "process";
	private boolean isProcessed;

	private BuddyConnectResponseMessageDTO(UUID id, UserDTO user, String message, boolean isProcessed, Date creationTime)
	{
		super(id, user, message, creationTime);
		this.isProcessed = isProcessed;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (!isProcessed)
		{
			possibleActions.add(PROCESS);
		}
		return possibleActions;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	public static BuddyConnectResponseMessageDTO createInstance(UserDTO requestingUser, BuddyConnectResponseMessage messageEntity)
	{
		return new BuddyConnectResponseMessageDTO(messageEntity.getID(), UserDTO.createInstance(messageEntity.getUser()),
				messageEntity.getMessage(), messageEntity.isProcessed(), messageEntity.getCreationTime());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(BuddyConnectResponseMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO requestingUser, Message messageEntity)
		{
			return BuddyConnectResponseMessageDTO.createInstance(requestingUser, (BuddyConnectResponseMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case PROCESS:
					return handleAction_Process(actingUser, (BuddyConnectResponseMessage) messageEntity, requestPayload);
				default:
					throw new IllegalArgumentException("Action '" + action + "' is not supported");
			}
		}

		private MessageActionDTO handleAction_Process(UserDTO requestingUser,
				BuddyConnectResponseMessage connectResponseMessageEntity, MessageActionDTO payload)
		{

			if (connectResponseMessageEntity.getStatus() == Status.REJECTED)
			{
				buddyService.removeBuddyAfterConnectRejection(requestingUser.getID(), connectResponseMessageEntity.getBuddyID());
			}
			else
			{
				buddyService.updateBuddyWithSecretUserInfo(connectResponseMessageEntity.getBuddyID(),
						connectResponseMessageEntity.getRelatedLoginID());
			}

			updateMessageStatusAsProcessed(connectResponseMessageEntity);

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private void updateMessageStatusAsProcessed(BuddyConnectResponseMessage connectResponseMessageEntity)
		{
			connectResponseMessageEntity.setProcessed();
			Message.getRepository().save(connectResponseMessageEntity);
		}
	}
}
