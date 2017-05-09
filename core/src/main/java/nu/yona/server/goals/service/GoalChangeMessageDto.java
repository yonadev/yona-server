/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.entities.GoalChangeMessage;
import nu.yona.server.goals.entities.GoalChangeMessage.Change;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDto;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDto;
import nu.yona.server.messaging.service.MessageActionDto;
import nu.yona.server.messaging.service.MessageDto;
import nu.yona.server.messaging.service.MessageService.TheDtoManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.service.UserDto;

@JsonRootName("goalChangeMessage")
public class GoalChangeMessageDto extends BuddyMessageLinkedUserDto
{
	public static final String GOAL_REL_NAME = "goal";
	private final UUID activityCategoryIdOfChangedGoal;
	private final Change change;

	private GoalChangeMessageDto(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
			UUID activityCategoryIdOfChangedGoal, Change change, String message)
	{
		super(id, creationTime, isRead, senderInfo, message);

		this.activityCategoryIdOfChangedGoal = activityCategoryIdOfChangedGoal;
		this.change = change;
	}

	@Override
	public String getType()
	{
		return "GoalChangeMessage";
	}

	@JsonIgnore
	public UUID getActivityCategoryIdOfChangedGoal()
	{
		return activityCategoryIdOfChangedGoal;
	}

	public Change getChange()
	{
		return change;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static GoalChangeMessageDto createInstance(GoalChangeMessage messageEntity, SenderInfo senderInfo)
	{
		return new GoalChangeMessageDto(messageEntity.getId(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getActivityCategoryOfChangedGoal().getId(), messageEntity.getChange(),
				messageEntity.getMessage());
	}

	@Component
	private static class Manager extends BuddyMessageDto.Manager
	{
		@Autowired
		private TheDtoManager theDtoFactory;

		@PostConstruct
		private void init()
		{
			theDtoFactory.addManager(GoalChangeMessage.class, this);
		}

		@Override
		public MessageDto createInstance(UserDto actingUser, Message messageEntity)
		{
			return GoalChangeMessageDto.createInstance((GoalChangeMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action,
				MessageActionDto requestPayload)
		{
			actingUser.assertMobileNumberConfirmed();

			switch (action)
			{
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}
	}
}
