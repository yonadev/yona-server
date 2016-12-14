/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.ActivityCommentMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDTO;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.MessageServiceException;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("activityCommentMessage")
public class ActivityCommentMessageDTO extends BuddyMessageLinkedUserDTO
{
	private static final String MESSAGE_PROPERTY = "message";
	private static final String REPLY = "reply";
	private final UUID activityId;
	private final Optional<UUID> repliedMessageId;
	private final UUID threadHeadMessageId;

	private ActivityCommentMessageDTO(UUID id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, UUID activityId,
			UUID threadHeadMessageId, Optional<UUID> repliedMessageId, String message)
	{
		super(id, creationTime, isRead, senderInfo, message);
		this.activityId = activityId;
		this.threadHeadMessageId = threadHeadMessageId;
		this.repliedMessageId = repliedMessageId;
	}

	@Override
	public String getType()
	{
		return "ActivityCommentMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
		if (this.isSentFromBuddy())
		{
			possibleActions.add(REPLY);
		}
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	@JsonIgnore
	public UUID getActivityId()
	{
		return activityId;
	}

	public UUID getThreadHeadMessageId()
	{
		return threadHeadMessageId;
	}

	@JsonIgnore
	public Optional<UUID> getRepliedMessageId()
	{
		return repliedMessageId;
	}

	public static ActivityCommentMessageDTO createInstance(UserDTO actingUser, ActivityCommentMessage messageEntity,
			SenderInfo senderInfo)
	{
		return new ActivityCommentMessageDTO(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getIntervalActivityId(), messageEntity.getThreadHeadMessageId(),
				messageEntity.getRepliedMessageId(), messageEntity.getMessage());
	}

	@Component
	private static class Manager extends BuddyMessageDTO.Manager
	{

		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private ActivityService activityService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(ActivityCommentMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return ActivityCommentMessageDTO.createInstance(actingUser, (ActivityCommentMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case REPLY:
					return handleAction_Reply(actingUser, (ActivityCommentMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDTO handleAction_Reply(UserDTO actingUser, ActivityCommentMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			String message = requestPayload.getProperty(MESSAGE_PROPERTY);
			if (message == null)
			{
				throw MessageServiceException.missingMandatoryActionProperty(REPLY, MESSAGE_PROPERTY);
			}
			MessageDTO reply = activityService.replyToMessage(actingUser, messageEntity, message);
			return MessageActionDTO.createInstanceActionDone(reply);
		}
	}
}
