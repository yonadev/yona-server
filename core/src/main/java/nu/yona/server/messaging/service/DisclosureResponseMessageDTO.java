/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Set;
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
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("disclosureResponseMessage")
public class DisclosureResponseMessageDTO extends BuddyMessageLinkedUserDTO
{
	private final UUID targetGoalConflictGoalID;
	private final LocalDate targetGoalConflictDate;
	private final Status status;

	private DisclosureResponseMessageDTO(UUID id, SenderInfo sender, ZonedDateTime creationTime, boolean isRead, UserDTO user,
			Status status, String message, UUID targetGoalConflictMessageID, UUID targetGoalConflictGoalID,
			LocalDate targetGoalConflictDate)
	{
		super(id, sender, creationTime, isRead, targetGoalConflictMessageID, user, message);
		this.status = status;
		this.targetGoalConflictGoalID = targetGoalConflictGoalID;
		this.targetGoalConflictDate = targetGoalConflictDate;
	}

	@Override
	public String getType()
	{
		return "DisclosureResponseMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
		return possibleActions;
	}

	@JsonIgnore
	public UUID getTargetGoalConflictGoalID()
	{
		return targetGoalConflictGoalID;
	}

	@JsonIgnore
	public LocalDate getTargetGoalConflictDate()
	{
		return targetGoalConflictDate;
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

	public static DisclosureResponseMessageDTO createInstance(UserDTO actingUser, DisclosureResponseMessage messageEntity,
			SenderInfo sender)
	{
		GoalConflictMessage targetGoalConflictMessage = messageEntity.getTargetGoalConflictMessage();
		return new DisclosureResponseMessageDTO(messageEntity.getID(), sender, messageEntity.getCreationTime(),
				messageEntity.isRead(), UserDTO.createInstanceIfNotNull(messageEntity.getSenderUser()), messageEntity.getStatus(),
				messageEntity.getMessage(), targetGoalConflictMessage.getID(), targetGoalConflictMessage.getGoal().getID(),
				targetGoalConflictMessage.getActivity().getStartTime().toLocalDate());
	}

	@Component
	private static class Manager extends MessageDTO.Manager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(DisclosureResponseMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return DisclosureResponseMessageDTO.createInstance(actingUser, (DisclosureResponseMessage) messageEntity,
					getSender(actingUser, messageEntity));
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}
	}
}
