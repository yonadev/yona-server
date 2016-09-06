/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;

@JsonRootName("buddyConnectResponseMessage")
public class BuddyConnectResponseMessageDTO extends BuddyMessageLinkedUserDTO
{
	private static final String PROCESS = "process";
	private final Status status;
	private final boolean isProcessed;

	private BuddyConnectResponseMessageDTO(UUID id, SenderInfo senderInfo, ZonedDateTime creationTime, boolean isRead,
			Optional<UserDTO> user, String message, Status status, boolean isProcessed)
	{
		super(id, senderInfo, creationTime, isRead, user, message);
		this.status = status;
		this.isProcessed = isProcessed;
	}

	@Override
	public String getType()
	{
		return "BuddyConnectResponseMessage";
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

	public Status getStatus()
	{
		return status;
	}

	@JsonIgnore
	public boolean isProcessed()
	{
		return isProcessed;
	}

	@Override
	public boolean canBeDeleted()
	{
		return this.isProcessed;
	}

	public static BuddyConnectResponseMessageDTO createInstance(UserDTO actingUser, BuddyConnectResponseMessage messageEntity,
			SenderInfo senderInfo)
	{
		return new BuddyConnectResponseMessageDTO(messageEntity.getID(), senderInfo, messageEntity.getCreationTime(),
				messageEntity.isRead(), UserDTO.createInstance(messageEntity.getSenderUser()), messageEntity.getMessage(),
				messageEntity.getStatus(), messageEntity.isProcessed());
	}

	@Component
	static class Manager extends MessageDTO.Manager
	{
		private static final Logger logger = LoggerFactory.getLogger(Manager.class);

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
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return BuddyConnectResponseMessageDTO.createInstance(actingUser, (BuddyConnectResponseMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
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
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		@Override
		protected SenderInfo getSenderInfoExtensionPoint(Message messageEntity)
		{
			// The buddy entity does not contain the user anonymized ID yet
			BuddyConnectResponseMessage responseMmessageEntity = (BuddyConnectResponseMessage) messageEntity;
			return SenderInfo.createInstanceBuddyDetached(responseMmessageEntity.getSenderUserID(),
					responseMmessageEntity.getSenderNickname());
		}

		MessageActionDTO handleAction_Process(UserDTO actingUser, BuddyConnectResponseMessage connectResponseMessageEntity,
				MessageActionDTO payload)
		{

			if (connectResponseMessageEntity.getStatus() == Status.REJECTED)
			{
				buddyService.removeBuddyAfterConnectRejection(actingUser.getID(), connectResponseMessageEntity.getBuddyID());
			}
			else
			{
				buddyService.setBuddyAcceptedWithSecretUserInfo(connectResponseMessageEntity.getBuddyID(),
						connectResponseMessageEntity.getRelatedUserAnonymizedID().get(),
						connectResponseMessageEntity.getSenderNickname());
			}

			connectResponseMessageEntity = updateMessageStatusAsProcessed(connectResponseMessageEntity);

			String mobileNumber = connectResponseMessageEntity.getSenderUser().map(u -> u.getMobileNumber())
					.orElse("already deleted");
			String id = connectResponseMessageEntity.getSenderUser().map(u -> u.getID().toString()).orElse("already deleted");
			logger.info(
					"User with mobile number '{}' and ID '{}' processed buddy connect response from user with mobile number '{}' and ID '{}'",
					actingUser.getMobileNumber(), actingUser.getID(), mobileNumber, id);

			return MessageActionDTO
					.createInstanceActionDone(theDTOFactory.createInstance(actingUser, connectResponseMessageEntity));
		}

		private BuddyConnectResponseMessage updateMessageStatusAsProcessed(
				BuddyConnectResponseMessage connectResponseMessageEntity)
		{
			connectResponseMessageEntity.setProcessed();
			return Message.getRepository().save(connectResponseMessageEntity);
		}
	}
}
