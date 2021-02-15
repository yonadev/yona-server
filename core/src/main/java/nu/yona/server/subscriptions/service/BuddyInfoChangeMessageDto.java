/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.LinkRelation;
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
import nu.yona.server.subscriptions.entities.BuddyInfoChangeMessage;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("buddyInfoChangeMessage")
public class BuddyInfoChangeMessageDto extends BuddyMessageLinkedUserDto
{
	private static final String PROCESS_REL_NAME = "process";
	private static final LinkRelation PROCESS_REL = LinkRelation.of(PROCESS_REL_NAME);
	private final boolean isProcessed;

	private BuddyInfoChangeMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, String message,
			boolean isProcessed)
	{
		super(id, creationTime, isRead, senderInfo, message);
		this.isProcessed = isProcessed;
	}

	@Override
	public String getType()
	{
		return "BuddyInfoChangeMessage";
	}

	@JsonIgnore
	public boolean isProcessed()
	{
		return isProcessed;
	}

	@Override
	public Set<LinkRelation> getPossibleActions()
	{
		Set<LinkRelation> possibleActions = super.getPossibleActions();
		if (!isProcessed)
		{
			possibleActions.add(PROCESS_REL);
		}
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return this.isProcessed;
	}

	public static BuddyInfoChangeMessageDto createInstance(BuddyInfoChangeMessage messageEntity, SenderInfo senderInfo)
	{
		return new BuddyInfoChangeMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getMessage(), messageEntity.isProcessed());
	}

	@Component
	static class Manager extends BuddyMessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(BuddyInfoChangeMessage.class, this);
		}

		@Override
		public MessageDto createInstance(User actingUser, Message messageEntity)
		{
			return BuddyInfoChangeMessageDto
					.createInstance((BuddyInfoChangeMessage) messageEntity, getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(User actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				case PROCESS_REL_NAME:
					return handleAction_Process(actingUser, (BuddyInfoChangeMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDto handleAction_Process(User actingUser, BuddyInfoChangeMessage messageEntity,
				MessageActionDto requestPayload)
		{
			if (messageEntity.getRelatedUserAnonymizedId().isPresent())
			{
				buddyService.updateBuddyUserInfo(actingUser.getId(), messageEntity.getRelatedUserAnonymizedId().get(),
						messageEntity.getNewFirstName(), messageEntity.getNewLastName(), messageEntity.getNewNickname(),
						messageEntity.getNewUserPhotoId());
			}

			messageEntity = updateMessageStatusAsProcessed(messageEntity);

			return MessageActionDto.createInstanceActionDone(theDtoFactory.createInstance(actingUser, messageEntity));
		}

		private BuddyInfoChangeMessage updateMessageStatusAsProcessed(BuddyInfoChangeMessage messageEntity)
		{
			messageEntity.setProcessed();
			return Message.getRepository().save(messageEntity);
		}
	}
}
