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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.MessageServiceException;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;

@JsonRootName("buddyConnectResponseMessage")
public class BuddyConnectResponseMessageDTO extends BuddyMessageDTO
{
	private static final String PROCESS = "process";
	private Status status;
	private boolean isProcessed;

	private BuddyConnectResponseMessageDTO(UUID id, Date creationTime, UserDTO user, String nickname, String message,
			Status status, boolean isProcessed)
	{
		super(id, creationTime, user, nickname, message);
		this.status = status;
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

	public Status getStatus()
	{
		return status;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	public static BuddyConnectResponseMessageDTO createInstance(UserDTO requestingUser, BuddyConnectResponseMessage messageEntity)
	{
		return new BuddyConnectResponseMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(),
				UserDTO.createInstance(messageEntity.getUser()), messageEntity.getNickname(), messageEntity.getMessage(),
				messageEntity.getStatus(), messageEntity.isProcessed());
	}

	@Component
	private static class Factory implements DTOManager
	{
		private static final Logger logger = LoggerFactory.getLogger(Factory.class);

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
			actingUser.assertMobileNumberConfirmed();

			switch (action)
			{
				case PROCESS:
					return handleAction_Process(actingUser, (BuddyConnectResponseMessage) messageEntity, requestPayload);
				default:
					throw MessageServiceException.actionNotSupported(action);
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
				buddyService.setBuddyAcceptedWithSecretUserInfo(connectResponseMessageEntity.getBuddyID(),
						connectResponseMessageEntity.getRelatedUserAnonymizedID(), connectResponseMessageEntity.getNickname());
			}

			updateMessageStatusAsProcessed(connectResponseMessageEntity);

			logger.info(
					"User with mobile number '{}' and ID '{}' processed buddy connect response from user with mobile number '{}' and ID '{}'",
					requestingUser.getMobileNumber(), requestingUser.getID(),
					connectResponseMessageEntity.getUser().getMobileNumber(), connectResponseMessageEntity.getUser().getID());

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private void updateMessageStatusAsProcessed(BuddyConnectResponseMessage connectResponseMessageEntity)
		{
			connectResponseMessageEntity.setProcessed();
			Message.getRepository().save(connectResponseMessageEntity);
		}
	}
}
