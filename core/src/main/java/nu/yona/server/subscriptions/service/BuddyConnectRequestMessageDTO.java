/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.exceptions.InvalidMessageActionException;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;

@JsonRootName("buddyConnectRequestMessage")
public class BuddyConnectRequestMessageDTO extends BuddyConnectMessageDTO
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private boolean isAccepted;
	private boolean isRejected;

	private BuddyConnectRequestMessageDTO(BuddyConnectRequestMessage buddyConnectRequestMessageEntity, UUID id, UserDTO user,
			UUID loginID, String nickname, String message, boolean isAccepted, boolean isRejected)
	{
		super(id, user, message);
		if (buddyConnectRequestMessageEntity == null)
		{
			throw new IllegalArgumentException("buddyConnectRequestMessageEntity cannot be null");
		}
		if (loginID == null)
		{
			throw new IllegalArgumentException("loginID cannot be null");
		}
		this.isAccepted = isAccepted;
		this.isRejected = isRejected;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (!isAccepted && !isRejected)
		{
			possibleActions.add(ACCEPT);
			possibleActions.add(REJECT);
		}
		return possibleActions;
	}

	public boolean isAccepted()
	{
		return isAccepted;
	}

	public boolean isRejected()
	{
		return isRejected;
	}

	public static BuddyConnectRequestMessageDTO createInstance(UserDTO requestingUser, BuddyConnectRequestMessage messageEntity)
	{
		return new BuddyConnectRequestMessageDTO(messageEntity, messageEntity.getID(),
				UserDTO.createInstance(messageEntity.getUser()), messageEntity.getRelatedLoginID(), messageEntity.getNickname(),
				messageEntity.getMessage(), messageEntity.isAccepted(), messageEntity.isRejected());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@Autowired
		private UserService userService;

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
			switch (action)
			{
				case ACCEPT:
					return handleAction_Accept(actingUser, (BuddyConnectRequestMessage) messageEntity, requestPayload);
				case REJECT:
					return handleAction_Reject(actingUser, (BuddyConnectRequestMessage) messageEntity, requestPayload);
				default:
					throw new IllegalArgumentException("Action '" + action + "' is not supported");
			}
		}

		private MessageActionDTO handleAction_Accept(UserDTO acceptingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, MessageActionDTO payload)
		{

			BuddyDTO buddy = buddyService.addBuddyToAcceptingUser(connectRequestMessageEntity.getUser().getID(),
					connectRequestMessageEntity.getNickname(), connectRequestMessageEntity.getRelatedLoginID());

			userService.addBuddy(acceptingUser, buddy);

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

		private MessageActionDTO handleAction_Delete(UserDTO user, BuddyConnectRequestMessage connectRequestMessageEntity,
				MessageActionDTO payload)
		{
			if (!connectRequestMessageEntity.isAccepted() && !connectRequestMessageEntity.isRejected())
			{
				throw InvalidMessageActionException.unprocessedMessageCannotBeDeleted();
			}

			MessageDestination destination = MessageDestination.getRepository()
					.findOne(user.getPrivateData().getNamedMessageDestinationID());
			destination.remove(connectRequestMessageEntity);
			MessageDestination.getRepository().save(destination);
			Message.getRepository().delete(connectRequestMessageEntity);

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
			MessageDestination messageDestination = connectRequestMessageEntity.getUser().getNamedMessageDestination();
			assert messageDestination != null;
			messageDestination.send(BuddyConnectResponseMessage.createInstance(respondingUser.getID(),
					respondingUser.getPrivateData().getVpnProfile().getLoginID(),
					respondingUser.getPrivateData().getAnonymousMessageDestinationID(),
					respondingUser.getPrivateData().getNickName(), responseMessage, connectRequestMessageEntity.getBuddyID(),
					connectRequestMessageEntity.getStatus()));
			MessageDestination.getRepository().save(messageDestination);
		}
	}
}
