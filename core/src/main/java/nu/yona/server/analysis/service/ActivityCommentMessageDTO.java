/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
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
	private final UUID activityID;
	private final Optional<UUID> repliedMessageID;
	private final UUID threadHeadMessageID;

	private ActivityCommentMessageDTO(UUID id, SenderInfo sender, ZonedDateTime creationTime, boolean isRead,
			Optional<UserDTO> senderUser, UUID activityID, UUID threadHeadMessageID, Optional<UUID> repliedMessageID,
			String message)
	{
		super(id, sender, creationTime, isRead, senderUser, message);
		this.activityID = activityID;
		this.threadHeadMessageID = threadHeadMessageID;
		this.repliedMessageID = repliedMessageID;
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
	public UUID getActivityID()
	{
		return activityID;
	}

	public UUID getThreadHeadMessageID()
	{
		return threadHeadMessageID;
	}

	@JsonIgnore
	public Optional<UUID> getRepliedMessageID()
	{
		return repliedMessageID;
	}

	public static ActivityCommentMessageDTO createInstance(UserDTO actingUser, ActivityCommentMessage messageEntity,
			SenderInfo sender)
	{
		return new ActivityCommentMessageDTO(messageEntity.getID(), sender, messageEntity.getCreationTime(),
				messageEntity.isRead(), UserDTO.createInstance(messageEntity.getSenderUser()),
				messageEntity.getActivityID(), messageEntity.getThreadHeadMessageID(), messageEntity.getRepliedMessageID(),
				messageEntity.getMessage());
	}

	@Component
	private static class Manager extends MessageDTO.Manager
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
					getSender(actingUser, messageEntity));
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
