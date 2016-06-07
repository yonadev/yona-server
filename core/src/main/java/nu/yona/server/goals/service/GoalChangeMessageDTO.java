/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
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
import nu.yona.server.messaging.service.BuddyMessageLinkedUserDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.MessageServiceException;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("goalChangeMessage")
public class GoalChangeMessageDTO extends BuddyMessageLinkedUserDTO
{
	public static final String GOAL_REL_NAME = "goal";
	private GoalDTO changedGoal;
	private Change change;

	private GoalChangeMessageDTO(UUID id, ZonedDateTime creationTime, UserDTO user, String nickname, GoalDTO changedGoal,
			Change change, String message)
	{
		super(id, creationTime, user, nickname, message);

		this.changedGoal = changedGoal;
		this.change = change;
	}

	@Override
	public String getType()
	{
		return "GoalChangeMessage";
	}

	@JsonIgnore
	public GoalDTO getChangedGoal()
	{
		return changedGoal;
	}

	public Change getChange()
	{
		return change;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static GoalChangeMessageDTO createInstance(UserDTO actingUser, GoalChangeMessage messageEntity)
	{
		return new GoalChangeMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(),
				UserDTO.createInstanceIfNotNull(messageEntity.getSenderUser()), messageEntity.getSenderNickname(),
				GoalDTO.createInstance(messageEntity.getChangedGoal()), messageEntity.getChange(), messageEntity.getMessage());
	}

	@Component
	private static class Factory implements DTOManager
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
			return GoalChangeMessageDTO.createInstance(actingUser, (GoalChangeMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			actingUser.assertMobileNumberConfirmed();

			switch (action)
			{
				default:
					throw MessageServiceException.actionNotSupported(action);
			}
		}
	}
}
