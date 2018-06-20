/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.messaging.entities.DisclosureResponseMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.subscriptions.service.UserDto;

@JsonRootName("disclosureResponseMessage")
public class DisclosureResponseMessageDto extends BuddyMessageLinkedUserDto
{
	private final UUID goalId;
	private final LocalDate goalConflictStartTime;
	private final Status status;

	private DisclosureResponseMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			Status status, String message, Optional<Long> targetGoalConflictMessageId, UUID goalId,
			LocalDate goalConflictStartTime)
	{
		super(id, creationTime, isRead, targetGoalConflictMessageId, senderInfo, message);
		this.status = status;
		this.goalId = goalId;
		this.goalConflictStartTime = goalConflictStartTime;
	}

	@Override
	public String getType()
	{
		return "DisclosureResponseMessage";
	}

	@JsonIgnore
	public UUID getGoalId()
	{
		return goalId;
	}

	@JsonIgnore
	public LocalDate getGoalConflictStartTime()
	{
		return goalConflictStartTime;
	}

	public Status getStatus()
	{
		return status;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static DisclosureResponseMessageDto createInstance(DisclosureResponseMessage messageEntity, SenderInfo senderInfo)
	{
		GoalConflictMessage targetGoalConflictMessage = messageEntity.getTargetGoalConflictMessage();
		return new DisclosureResponseMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getStatus(), messageEntity.getMessage(), Optional.of(targetGoalConflictMessage.getId()),
				targetGoalConflictMessage.getGoal().getId(),
				targetGoalConflictMessage.getActivity().getStartTime().toLocalDate());
	}

	@Component
	static class Manager extends BuddyMessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(DisclosureResponseMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return DisclosureResponseMessageDto.createInstance((DisclosureResponseMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			switch (action)
			{
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}
	}
}
