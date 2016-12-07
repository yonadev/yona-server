/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.goals.entities.GoalChangeMessage;
import nu.yona.server.goals.entities.GoalChangeMessage.Change;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageDTO;
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("goalChangeMessage")
public class GoalChangeMessageDTO extends BuddyMessageLinkedUserDTO
{
	public static final String GOAL_REL_NAME = "goal";
	private final UUID activityCategoryIdOfChangedGoal;
	private final Change change;

	private GoalChangeMessageDTO(long id, LocalDateTime creationTime, boolean isRead, SenderInfo senderInfo,
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
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static GoalChangeMessageDTO createInstance(UserDTO actingUser, GoalChangeMessage messageEntity, SenderInfo senderInfo)
	{
		return new GoalChangeMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), messageEntity.isRead(),
				senderInfo, messageEntity.getActivityCategoryOfChangedGoal().getID(), messageEntity.getChange(),
				messageEntity.getMessage());
	}

	@Component
	private static class Manager extends BuddyMessageDTO.Manager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(GoalChangeMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return GoalChangeMessageDTO.createInstance(actingUser, (GoalChangeMessage) messageEntity,
					getSenderInfo(actingUser, messageEntity));
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
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
