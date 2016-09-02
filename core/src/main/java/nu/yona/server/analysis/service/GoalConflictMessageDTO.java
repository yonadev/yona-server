/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.Constants;
import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.messaging.entities.DisclosureRequestMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.SenderInfo;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("goalConflictMessage")
public class GoalConflictMessageDTO extends MessageDTO
{
	private static final String REQUEST_DISCLOSURE = "requestDisclosure";
	private final Optional<String> url;
	private final Status status;
	private final ZonedDateTime activityStartTime;
	private final ZonedDateTime activityEndTime;
	private final UUID goalID;
	private final UUID activityCategoryID;

	private GoalConflictMessageDTO(UUID id, SenderInfo sender, ZonedDateTime creationTime, boolean isRead, UUID goalID,
			UUID activityCategoryID, Optional<String> url, Status status, ZonedDateTime activityStartTime,
			ZonedDateTime activityEndTime)
	{
		super(id, sender, creationTime, isRead);
		this.goalID = goalID;
		this.activityCategoryID = activityCategoryID;
		this.url = url;
		this.status = status;
		this.activityStartTime = activityStartTime;
		this.activityEndTime = activityEndTime;
	}

	@Override
	public String getType()
	{
		return "GoalConflictMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = super.getPossibleActions();
		if (this.isSentFromBuddy() && this.status == Status.ANNOUNCED)
		{
			possibleActions.add(REQUEST_DISCLOSURE);
		}
		return possibleActions;
	}

	@JsonIgnore
	public UUID getGoalID()
	{
		return goalID;
	}

	@JsonIgnore
	public UUID getActivityCategoryID()
	{
		return activityCategoryID;
	}

	public Status getStatus()
	{
		return status;
	}

	public Optional<String> getUrl()
	{
		return url;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public ZonedDateTime getActivityStartTime()
	{
		return activityStartTime;
	}

	@JsonFormat(pattern = Constants.ISO_DATE_PATTERN)
	public ZonedDateTime getActivityEndTime()
	{
		return activityEndTime;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	private static GoalConflictMessageDTO createInstance(GoalConflictMessage messageEntity, SenderInfo sender)
	{
		return new GoalConflictMessageDTO(messageEntity.getID(), sender, messageEntity.getCreationTime(), messageEntity.isRead(),
				messageEntity.getGoal().getID(), messageEntity.getActivity().getActivityCategory().getID(),
				messageEntity.isUrlDisclosed() ? messageEntity.getURL() : Optional.empty(), messageEntity.getStatus(),
				messageEntity.getActivity().getStartTime(), messageEntity.getActivity().getEndTime());
	}

	@Component
	private static class Manager extends MessageDTO.Manager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private UserAnonymizedService userAnonymizedService;

		@Autowired
		private MessageService messageService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(GoalConflictMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return GoalConflictMessageDTO.createInstance((GoalConflictMessage) messageEntity,
					getSender(actingUser, messageEntity));
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case REQUEST_DISCLOSURE:
					return handleAction_RequestDisclosure(actingUser, (GoalConflictMessage) messageEntity, requestPayload);
				default:
					return super.handleAction(actingUser, messageEntity, action, requestPayload);
			}
		}

		private MessageActionDTO handleAction_RequestDisclosure(UserDTO actingUser, GoalConflictMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			messageEntity = updateMessageStatusAsDisclosureRequested(messageEntity);

			MessageDestinationDTO messageDestination = userAnonymizedService
					.getUserAnonymized(messageEntity.getRelatedUserAnonymizedID().get()).getAnonymousDestination();
			messageService.sendMessage(
					DisclosureRequestMessage.createInstance(actingUser.getID(), actingUser.getPrivateData().getUserAnonymizedID(),
							actingUser.getPrivateData().getNickname(), requestPayload.getProperty("message"), messageEntity),
					messageDestination);

			return MessageActionDTO.createInstanceActionDone(theDTOFactory.createInstance(actingUser, messageEntity));
		}

		private GoalConflictMessage updateMessageStatusAsDisclosureRequested(GoalConflictMessage messageEntity)
		{
			messageEntity.setStatus(Status.DISCLOSURE_REQUESTED);
			return GoalConflictMessage.getRepository().save(messageEntity);
		}
	}
}
