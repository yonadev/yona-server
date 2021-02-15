/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import org.springframework.hateoas.LinkRelation;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nu.yona.server.messaging.entities.BuddyMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyConnectionChangeMessage;

public abstract class BuddyMessageEmbeddedUserDto extends BuddyMessageDto
{
	private Map<String, Object> embeddedResources = Collections.emptyMap();

	protected BuddyMessageEmbeddedUserDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			String message)
	{
		super(id, creationTime, isRead, senderInfo, message);
	}

	@Override
	@JsonIgnore
	public boolean isToIncludeSenderUserLink()
	{
		return false; // User is embedded
	}

	@JsonProperty("_embedded")
	@JsonInclude(Include.NON_EMPTY)
	public Map<String, Object> getEmbeddedResources()
	{
		return embeddedResources;
	}

	public void setEmbeddedUser(LinkRelation rel, Object userResource)
	{
		embeddedResources = Collections.singletonMap(rel.value(), userResource);
	}

	@Component
	public abstract static class Manager extends BuddyMessageDto.Manager
	{
		@Override
		protected SenderInfo createSenderInfoForBuddy(Buddy buddy, Message messageEntity)
		{
			return getSenderInfoExtensionPoint(messageEntity);
		}

		@Override
		protected SenderInfo getSenderInfoExtensionPoint(Message messageEntity)
		{
			return createSenderInfoForBuddyConnectionChangeMessage(((BuddyMessage) messageEntity).getSenderUser(),
					(BuddyConnectionChangeMessage) messageEntity);
		}
	}
}
