/*******************************************************************************
 * Copyright (c) 2016, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import nu.yona.server.messaging.service.BuddyMessageEmbeddedUserDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.BuddyDisconnectMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@JsonRootName("buddyDisconnectMessage")
public class BuddyDisconnectMessageDto extends BuddyMessageEmbeddedUserDto
{
	private static final String PROCESS = "process";
	private final DropBuddyReason reason;
	private final boolean isProcessed;

	private BuddyDisconnectMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, String message,
			DropBuddyReason reason, boolean isProcessed)
	{
		super(id, creationTime, isRead, senderInfo, message);
		this.reason = reason;
		this.isProcessed = isProcessed;
	}

	@Override
	public String getType()
	{
		return "BuddyDisconnectMessage";
	}

	public DropBuddyReason getReason()
	{
		return reason;
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

	public static BuddyDisconnectMessageDto createInstance(BuddyDisconnectMessage messageEntity, SenderInfo senderInfo)
	{
		return new BuddyDisconnectMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.getReason(), messageEntity.isProcessed());
	}

	@Component
	private static class Manager extends BuddyMessageEmbeddedUserDto.Manager
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
			theDtoFactory.addManager(BuddyDisconnectMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return BuddyDisconnectMessageDto.createInstance((BuddyDisconnectMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				case PROCESS:
					return handleAction_Process(actingUser, (BuddyDisconnectMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDto handleAction_Process(UserDto actingUser, BuddyDisconnectMessage messageEntity,
				MessageActionDto requestPayload)
		{
			buddyService.removeBuddyAfterBuddyRemovedConnection(actingUser.getId(), messageEntity.getSenderUserId());
			// refresh after actions
			actingUser = userService.getPrivateUser(actingUser.getId());

			messageEntity = updateMessageStatusAsProcessed(messageEntity);

			logHandledAction_Process(actingUser, messageEntity);

			return MessageActionDto.createInstanceActionDone(theDtoFactory.createInstance(actingUser, messageEntity));
		}

		private void logHandledAction_Process(UserDto actingUser, BuddyDisconnectMessage messageEntity)
		{
			Optional<User> senderUser = messageEntity.getSenderUser();
			String mobileNumber = senderUser.map(User::getMobileNumber).orElse("already deleted");
			String id = senderUser.map(u -> u.getId().toString()).orElse("already deleted");
			logger.info(
					"User with mobile number '{}' and ID '{}' processed buddy disconnect message from user with mobile number '{}' and ID '{}'",
					actingUser.getMobileNumber(), actingUser.getId(), mobileNumber, id);
		}

		private BuddyDisconnectMessage updateMessageStatusAsProcessed(BuddyDisconnectMessage messageEntity)
		{
			messageEntity.setProcessed();
			return Message.getRepository().save(messageEntity);
		}
	}
}
