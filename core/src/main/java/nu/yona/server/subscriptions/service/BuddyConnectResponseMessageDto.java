/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("buddyConnectResponseMessage")
public class BuddyConnectResponseMessageDto extends BuddyMessageLinkedUserDto
{
	private static final String PROCESS = "process";
	private final Status status;
	private final boolean isProcessed;

	private BuddyConnectResponseMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			String message, Status status, boolean isProcessed)
	{
		super(id, creationTime, isRead, senderInfo, message);
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

	public static BuddyConnectResponseMessageDto createInstance(BuddyConnectResponseMessage messageEntity, SenderInfo senderInfo)
	{
		return new BuddyConnectResponseMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.getStatus(), messageEntity.isProcessed());
	}

	@Component
	static class Manager extends BuddyMessageDto.Manager
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
			theDtoFactory.addManager(BuddyConnectResponseMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return BuddyConnectResponseMessageDto.createInstance((BuddyConnectResponseMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
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

		MessageActionDto handleAction_Process(UserDto actingUser, BuddyConnectResponseMessage connectResponseMessageEntity,
				MessageActionDto payload)
		{

			if (connectResponseMessageEntity.getStatus() == Status.REJECTED)
			{
				buddyService.removeBuddyAfterConnectRejection(actingUser.getId(), connectResponseMessageEntity.getBuddyId());
			}
			else
			{
				buddyService.setBuddyAcceptedWithSecretUserInfo(connectResponseMessageEntity.getBuddyId(),
						connectResponseMessageEntity.getRelatedUserAnonymizedId().get(),
						connectResponseMessageEntity.getSenderNickname());
			}
			UserDto actingUserAfterUpdate = userService.getPrivateUser(actingUser.getId());

			connectResponseMessageEntity = updateMessageStatusAsProcessed(connectResponseMessageEntity);

			Optional<User> senderUser = connectResponseMessageEntity.getSenderUser();
			String mobileNumber = senderUser.map(User::getMobileNumber).orElse("already deleted");
			String id = senderUser.map(u -> u.getId().toString()).orElse("already deleted");
			logger.info(
					"User with mobile number '{}' and ID '{}' processed buddy connect response from user with mobile number '{}' and ID '{}'",
					actingUser.getMobileNumber(), actingUser.getId(), mobileNumber, id);

			return MessageActionDto
					.createInstanceActionDone(theDtoFactory.createInstance(actingUserAfterUpdate, connectResponseMessageEntity));
		}

		private BuddyConnectResponseMessage updateMessageStatusAsProcessed(
				BuddyConnectResponseMessage connectResponseMessageEntity)
		{
			connectResponseMessageEntity.setProcessed();
			return Message.getRepository().save(connectResponseMessageEntity);
		}
	}
}
