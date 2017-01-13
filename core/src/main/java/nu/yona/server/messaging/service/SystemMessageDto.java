/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.SystemMessage;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.subscriptions.service.UserDto;

@JsonRootName("systemMessage")
public class SystemMessageDto extends MessageDto
{
	private final String message;

	private SystemMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, String message)
	{
		super(id, creationTime, isRead, senderInfo);
		this.message = message;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	@Override
	public String getType()
	{
		return "SystemMessage";
	}

	public String getMessage()
	{
		return message;
	}

	private static MessageDto createInstance(SystemMessage messageEntity, SenderInfo senderInfo)
	{
		return new SystemMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(), senderInfo,
				messageEntity.getMessage());
	}

	@Component
	private static class Manager extends MessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(GoalConflictMessage.class, this);
		}

		@Override
		protected SenderInfo getSenderInfoExtensionPoint(Message messageEntity)
		{
			return createSenderInfoForSystem();
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return SystemMessageDto.createInstance((SystemMessage) messageEntity, getSenderInfo(actingUser, messageEntity));
		}
	}
}
