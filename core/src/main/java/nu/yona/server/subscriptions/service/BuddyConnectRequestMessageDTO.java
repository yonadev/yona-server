/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDTO;
import nu.yona.server.messaging.service.BuddyMessageEmbeddedUserDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("buddyConnectRequestMessage")
public class BuddyConnectRequestMessageDTO extends BuddyMessageEmbeddedUserDTO
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private final Status status;

	private BuddyConnectRequestMessageDTO(UUID id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			String message, Status status)
	{
		super(id, creationTime, isRead, senderInfo, message);

		this.status = status;
	}

	@Override
	public String getType()
	{
		return "BuddyConnectRequestMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
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

	@Override
	public boolean canBeDeleted()
	{
		return this.status == Status.ACCEPTED || this.status == Status.REJECTED;
	}

	public static BuddyConnectRequestMessageDTO createInstance(UserDTO actingUser, BuddyConnectRequestMessage messageEntity,
			SenderInfo senderInfo)
	{

		if (messageEntity == null)
		{
			throw BuddyServiceException.messageEntityCannotBeNull();
		}

		if (!messageEntity.getRelatedUserAnonymizedID().isPresent())
		{
			throw BuddyServiceException.userAnonymizedIdCannotBeNull();
		}

		return new BuddyConnectRequestMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.getStatus());
	}

	@Component
	private static class Manager extends BuddyMessageDTO.Manager
	{
		private static final Logger logger = LoggerFactory.getLogger(Manager.class);

		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(BuddyConnectRequestMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return BuddyConnectRequestMessageDTO.createInstance(actingUser, (BuddyConnectRequestMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
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
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDTO handleAction_Accept(UserDTO actingUser, BuddyConnectRequestMessage connectRequestMessageEntity,
				MessageActionDTO payload)
		{
			if (!connectRequestMessageEntity.getSenderUser().isPresent())
			{
				throw UserServiceException.notFoundByID(connectRequestMessageEntity.getSenderUserID());
			}
			User senderUser = connectRequestMessageEntity.getSenderUser().get();
			buddyService.addBuddyToAcceptingUser(actingUser, senderUser.getID(), connectRequestMessageEntity.getSenderNickname(),
					connectRequestMessageEntity.getRelatedUserAnonymizedID().get(),
					connectRequestMessageEntity.requestingSending(), connectRequestMessageEntity.requestingReceiving());

			connectRequestMessageEntity = updateMessageStatusAsAccepted(connectRequestMessageEntity);

			sendResponseMessageToRequestingUser(actingUser, connectRequestMessageEntity, payload.getProperty("message"));

			logger.info(
					"User with mobile number '{}' and ID '{}' accepted buddy connect request from user with mobile number '{}' and ID '{}'",
					actingUser.getMobileNumber(), actingUser.getID(), senderUser.getMobileNumber(), senderUser.getID());

			return MessageActionDTO
					.createInstanceActionDone(theDTOFactory.createInstance(actingUser, connectRequestMessageEntity));
		}

		private MessageActionDTO handleAction_Reject(UserDTO actingUser, BuddyConnectRequestMessage connectRequestMessageEntity,
				MessageActionDTO payload)
		{
			connectRequestMessageEntity = updateMessageStatusAsRejected(connectRequestMessageEntity);

			sendResponseMessageToRequestingUser(actingUser, connectRequestMessageEntity, payload.getProperty("message"));

			String mobileNumber = connectRequestMessageEntity.getSenderUser().map(u -> u.getMobileNumber())
					.orElse("already deleted");
			String id = connectRequestMessageEntity.getSenderUser().map(u -> u.getID().toString()).orElse("already deleted");
			logger.info(
					"User with mobile number '{}' and ID '{}' rejected buddy connect request from user with mobile number '{}' and ID '{}'",
					actingUser.getMobileNumber(), actingUser.getID(), mobileNumber, id);

			return MessageActionDTO
					.createInstanceActionDone(theDTOFactory.createInstance(actingUser, connectRequestMessageEntity));
		}

		private BuddyConnectRequestMessage updateMessageStatusAsAccepted(BuddyConnectRequestMessage connectRequestMessageEntity)
		{
			connectRequestMessageEntity.setStatus(BuddyAnonymized.Status.ACCEPTED);
			return Message.getRepository().save(connectRequestMessageEntity);
		}

		private BuddyConnectRequestMessage updateMessageStatusAsRejected(BuddyConnectRequestMessage connectRequestMessageEntity)
		{
			connectRequestMessageEntity.setStatus(BuddyAnonymized.Status.REJECTED);
			return Message.getRepository().save(connectRequestMessageEntity);
		}

		private void sendResponseMessageToRequestingUser(UserDTO respondingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, String responseMessage)
		{
			buddyService.sendBuddyConnectResponseMessage(respondingUser.getID(),
					respondingUser.getPrivateData().getUserAnonymizedID(), respondingUser.getPrivateData().getNickname(),
					connectRequestMessageEntity.getRelatedUserAnonymizedID().get(), connectRequestMessageEntity.getBuddyID(),
					connectRequestMessageEntity.getStatus(), responseMessage);
		}
	}
}
