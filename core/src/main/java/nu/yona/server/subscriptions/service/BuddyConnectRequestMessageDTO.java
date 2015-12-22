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
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;

@JsonRootName("buddyConnectRequestMessage")
public class BuddyConnectRequestMessageDTO extends BuddyMessageDTO
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private Status status;

	private BuddyConnectRequestMessageDTO(BuddyConnectRequestMessage buddyConnectRequestMessageEntity, UUID id, Date creationTime,
			UserDTO user, UUID userAnonymizedID, String nickname, String message, Status status)
	{
		super(id, creationTime, user, nickname, message);

		if (buddyConnectRequestMessageEntity == null)
		{
			throw BuddyServiceException.messageEntityCannotBeNull();
		}

		if (buddyConnectRequestMessageEntity.getRelatedUserAnonymizedID() == null)
		{
			throw BuddyServiceException.userAnonymizedIdCannotBeNull();
		}

		this.status = status;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (status == Status.REQUESTED)
		{
			possibleActions.add(ACCEPT);
			possibleActions.add(REJECT);
		}
		return possibleActions;
	}

	public Status getStatus()
	{
		return status;
	}

	public static BuddyConnectRequestMessageDTO createInstance(UserDTO requestingUser, BuddyConnectRequestMessage messageEntity)
	{
		return new BuddyConnectRequestMessageDTO(messageEntity, messageEntity.getID(), messageEntity.getCreationTime(),
				UserDTO.createInstance(messageEntity.getUser()), messageEntity.getRelatedUserAnonymizedID(),
				messageEntity.getNickname(), messageEntity.getMessage(), messageEntity.getStatus());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@Autowired
		private UserAnonymizedService userAnonymizedService;

		@Autowired
		private MessageService messageService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(BuddyConnectRequestMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return BuddyConnectRequestMessageDTO.createInstance(actingUser, (BuddyConnectRequestMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			actingUser.assertMobileNumberConfirmed();

			switch (action)
			{
				case ACCEPT:
					return handleAction_Accept(actingUser, (BuddyConnectRequestMessage) messageEntity, requestPayload);
				case REJECT:
					return handleAction_Reject(actingUser, (BuddyConnectRequestMessage) messageEntity, requestPayload);
				default:
					throw BuddyServiceException.actionUnknown(action);
			}
		}

		private MessageActionDTO handleAction_Accept(UserDTO acceptingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, MessageActionDTO payload)
		{
			buddyService.addBuddyToAcceptingUser(acceptingUser, connectRequestMessageEntity.getUser().getID(),
					connectRequestMessageEntity.getNickname(), connectRequestMessageEntity.getRelatedUserAnonymizedID(),
					connectRequestMessageEntity.requestingSending(), connectRequestMessageEntity.requestingReceiving());

			updateMessageStatusAsAccepted(connectRequestMessageEntity);

			sendResponseMessageToRequestingUser(acceptingUser, connectRequestMessageEntity, payload.getProperty("message"));

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private MessageActionDTO handleAction_Reject(UserDTO rejectingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, MessageActionDTO payload)
		{
			updateMessageStatusAsRejected(connectRequestMessageEntity);

			sendResponseMessageToRequestingUser(rejectingUser, connectRequestMessageEntity, payload.getProperty("message"));

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private void updateMessageStatusAsAccepted(BuddyConnectRequestMessage connectRequestMessageEntity)
		{
			connectRequestMessageEntity.setStatus(BuddyAnonymized.Status.ACCEPTED);
			Message.getRepository().save(connectRequestMessageEntity);
		}

		private void updateMessageStatusAsRejected(BuddyConnectRequestMessage connectRequestMessageEntity)
		{
			connectRequestMessageEntity.setStatus(BuddyAnonymized.Status.REJECTED);
			Message.getRepository().save(connectRequestMessageEntity);
		}

		private void sendResponseMessageToRequestingUser(UserDTO respondingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, String responseMessage)
		{
			MessageDestinationDTO messageDestination = userAnonymizedService
					.getUserAnonymized(connectRequestMessageEntity.getRelatedUserAnonymizedID()).getAnonymousDestination();
			assert messageDestination != null;
			messageService.sendMessage(
					BuddyConnectResponseMessage.createInstance(respondingUser.getID(),
							respondingUser.getPrivateData().getUserAnonymizedID(), respondingUser.getPrivateData().getNickname(),
							responseMessage, connectRequestMessageEntity.getBuddyID(), connectRequestMessageEntity.getStatus()),
					messageDestination);
		}
	}
}
