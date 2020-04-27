/*******************************************************************************
 * Copyright (c) 2018, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDto;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.BuddyVpnConnectionStatusChangeMessage;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("buddyVpnConnectionStatusChangeMessage")
public class BuddyVpnConnectionStatusChangeMessageDto extends BuddyMessageLinkedUserDto
{
	private BuddyVpnConnectionStatusChangeMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			String message)
	{
		super(id, creationTime, isRead, senderInfo, message);
	}

	@Override
	public String getType()
	{
		return "BuddyVpnConnectionStatusChangeMessage";
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static BuddyVpnConnectionStatusChangeMessageDto createInstance(BuddyVpnConnectionStatusChangeMessage messageEntity,
			SenderInfo senderInfo)
	{
		return new BuddyVpnConnectionStatusChangeMessageDto(messageEntity.getId(), messageEntity.getCreationTime(),
				messageEntity.isRead(), senderInfo, messageEntity.getMessage());
	}

	@Component
	static class Manager extends BuddyMessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(BuddyVpnConnectionStatusChangeMessage.class, this);
		}

		@Override
		public MessageDto createInstance(User actingUser, Message messageEntity)
		{
			return BuddyVpnConnectionStatusChangeMessageDto.createInstance((BuddyVpnConnectionStatusChangeMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}
	}
}