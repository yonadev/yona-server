/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.LinkRelation;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.ActivityCommentMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDto;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.messaging.service.MessageServiceException;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("activityCommentMessage")
public class ActivityCommentMessageDto extends BuddyMessageLinkedUserDto
{
	private static final String MESSAGE_PROPERTY = "message";
	private static final String REPLY_REL_NAME = "reply";
	private static final LinkRelation REPLY_REL = LinkRelation.of(REPLY_REL_NAME);
	private final long intervalActivityId;
	private final Optional<Long> repliedMessageId;
	private final long threadHeadMessageId;

	private ActivityCommentMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo, long activityId,
			long threadHeadMessageId, Optional<Long> repliedMessageId, String message)
	{
		super(id, creationTime, isRead, senderInfo, message);
		this.intervalActivityId = activityId;
		this.threadHeadMessageId = threadHeadMessageId;
		this.repliedMessageId = repliedMessageId;
	}

	@Override
	public String getType()
	{
		return "ActivityCommentMessage";
	}

	@Override
	public Set<LinkRelation> getPossibleActions()
	{
		Set<LinkRelation> possibleActions = super.getPossibleActions();
		if (this.isSentFromBuddy())
		{
			possibleActions.add(REPLY_REL);
		}
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	@JsonIgnore
	public long getIntervalActivityId()
	{
		return intervalActivityId;
	}

	public long getThreadHeadMessageId()
	{
		return threadHeadMessageId;
	}

	@JsonIgnore
	public Optional<Long> getRepliedMessageId()
	{
		return repliedMessageId;
	}

	private static ActivityCommentMessageDto createInstance(ActivityCommentMessage messageEntity, SenderInfo senderInfo)
	{
		return new ActivityCommentMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getIntervalActivity().getId(), messageEntity.getThreadHeadMessage().getId(),
				messageEntity.getRepliedMessage().map(Message::getId), messageEntity.getMessage());
	}

	@Component
	static class Manager extends BuddyMessageDto.Manager
	{

		@Autowired
		private TheDtoManager theDtoFactory;

		@Autowired
		private ActivityService activityService;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(ActivityCommentMessage.class, this);
		}

		@Override
		public MessageDto createInstance(User actingUser, Message messageEntity)
		{
			return ActivityCommentMessageDto.createInstance((ActivityCommentMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(User actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				case REPLY_REL_NAME:
					return handleAction_Reply(actingUser, (ActivityCommentMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDto handleAction_Reply(User actingUser, ActivityCommentMessage messageEntity,
				MessageActionDto requestPayload)
		{
			String message = requestPayload.getProperty(MESSAGE_PROPERTY);
			if (message == null)
			{
				throw MessageServiceException.missingMandatoryActionProperty(REPLY_REL_NAME, MESSAGE_PROPERTY);
			}
			MessageDto reply = activityService.replyToMessage(actingUser, messageEntity, message);
			return MessageActionDto.createInstanceActionDone(reply);
		}
	}
}
