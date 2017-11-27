/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDto;
import nu.yona.server.messaging.service.BuddyMessageEmbeddedUserDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("buddyConnectRequestMessage")
public class BuddyConnectRequestMessageDto extends BuddyMessageEmbeddedUserDto
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private final Status status;

	private BuddyConnectRequestMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
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

	public static BuddyConnectRequestMessageDto createInstance(BuddyConnectRequestMessage messageEntity, SenderInfo senderInfo)
	{

		if (messageEntity == null)
		{
			throw BuddyServiceException.messageEntityCannotBeNull();
		}

		if (!messageEntity.getRelatedUserAnonymizedId().isPresent())
		{
			throw BuddyServiceException.userAnonymizedIdCannotBeNull();
		}

		return new BuddyConnectRequestMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.getStatus());
	}

	@Component
	private static class Manager extends BuddyMessageDto.Manager
	{
		private static final Logger logger = LoggerFactory.getLogger(Manager.class);

		@Autowired
		private TheDtoManager theDtoFactory;

		@Autowired
		private BuddyService buddyService;

		@Autowired
		private UserService userService;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(BuddyConnectRequestMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return BuddyConnectRequestMessageDto.createInstance((BuddyConnectRequestMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
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

		private MessageActionDto handleAction_Accept(UserDto actingUser, BuddyConnectRequestMessage connectRequestMessageEntity,
				MessageActionDto payload)
		{
			buddyService.addBuddyToAcceptingUser(actingUser, connectRequestMessageEntity);

			connectRequestMessageEntity = updateMessageStatusAsAccepted(connectRequestMessageEntity);

			sendResponseMessageToRequestingUser(actingUser, connectRequestMessageEntity, payload.getProperty("message"));

			logHandledAction_Accept(actingUser, connectRequestMessageEntity.getSenderUser().get());

			return MessageActionDto
					.createInstanceActionDone(theDtoFactory.createInstance(actingUser, connectRequestMessageEntity));
		}

		private void logHandledAction_Accept(UserDto actingUser, User senderUser)
		{
			logger.info(
					"User with mobile number '{}' and ID '{}' accepted buddy connect request from user with mobile number '{}' and ID '{}'",
					actingUser.getMobileNumber(), actingUser.getId(), senderUser.getMobileNumber(), senderUser.getId());
		}

		private MessageActionDto handleAction_Reject(UserDto actingUser, BuddyConnectRequestMessage connectRequestMessageEntity,
				MessageActionDto payload)
		{
			connectRequestMessageEntity = updateMessageStatusAsRejected(connectRequestMessageEntity);

			sendResponseMessageToRequestingUser(actingUser, connectRequestMessageEntity, payload.getProperty("message"));

			logHandledAction_Reject(actingUser, connectRequestMessageEntity);

			return MessageActionDto
					.createInstanceActionDone(theDtoFactory.createInstance(actingUser, connectRequestMessageEntity));
		}

		private void logHandledAction_Reject(UserDto actingUser, BuddyConnectRequestMessage connectRequestMessageEntity)
		{
			String mobileNumber = connectRequestMessageEntity.getSenderUser().map(User::getMobileNumber)
					.orElse("already deleted");
			String id = connectRequestMessageEntity.getSenderUser().map(u -> u.getId().toString()).orElse("already deleted");
			logger.info(
					"User with mobile number '{}' and ID '{}' rejected buddy connect request from user with mobile number '{}' and ID '{}'",
					actingUser.getMobileNumber(), actingUser.getId(), mobileNumber, id);
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

		private void sendResponseMessageToRequestingUser(UserDto respondingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, String responseMessage)
		{
			User respondingUserEntity = userService.getUserEntityById(respondingUser.getId());
			buddyService.sendBuddyConnectResponseMessage(BuddyMessageDto.createBuddyInfoParametersInstance(respondingUser),
					connectRequestMessageEntity.getRelatedUserAnonymizedId().get(), connectRequestMessageEntity.getBuddyId(),
					respondingUserEntity.getDevices(), connectRequestMessageEntity.getStatus(), responseMessage);
		}
	}
}
