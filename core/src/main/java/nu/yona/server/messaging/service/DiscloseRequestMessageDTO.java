/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.analysis.service.GoalConflictMessageDTO;
import nu.yona.server.messaging.entities.DiscloseRequestMessage;
import nu.yona.server.messaging.entities.DiscloseResponseMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("discloseRequestMessage")
public class DiscloseRequestMessageDTO extends MessageDTO
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private String nickname;
	private Status status;

	private GoalConflictMessageDTO targetGoalConflictMessage;

	private DiscloseRequestMessageDTO(UUID id, Date creationTime, String nickname, Status status,
			UUID targetGoalConflictMessageOriginID, GoalConflictMessageDTO targetGoalConflictMessage)
	{
		super(id, creationTime, targetGoalConflictMessageOriginID);
		this.nickname = nickname;
		this.status = status;
		this.targetGoalConflictMessage = targetGoalConflictMessage;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (status == Status.DISCLOSE_REQUESTED)
		{
			possibleActions.add(ACCEPT);
			possibleActions.add(REJECT);
		}
		return possibleActions;
	}

	public String getNickname()
	{
		return nickname;
	}

	public GoalConflictMessageDTO getTargetGoalConflictMessage()
	{
		return targetGoalConflictMessage;
	}

	public Status getStatus()
	{
		return status;
	}

	@Override
	public boolean canBeDeleted()
	{
		return this.status == Status.DISCLOSE_ACCEPTED || this.status == Status.DISCLOSE_REJECTED;
	}

	public static DiscloseRequestMessageDTO createInstance(UserDTO actingUser, DiscloseRequestMessage messageEntity)
	{
		GoalConflictMessage targetGoalConflictMessage = messageEntity.getTargetGoalConflictMessage();
		return new DiscloseRequestMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), messageEntity.getNickname(),
				messageEntity.getStatus(), targetGoalConflictMessage.getOriginGoalConflictMessageID(),
				GoalConflictMessageDTO.createInstance(targetGoalConflictMessage, null));
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private MessageService messageService;

		@Autowired
		private UserAnonymizedService userAnonymizedService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(DiscloseRequestMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return DiscloseRequestMessageDTO.createInstance(actingUser, (DiscloseRequestMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case ACCEPT:
					return handleAction_Accept(actingUser, (DiscloseRequestMessage) messageEntity, requestPayload);
				case REJECT:
					return handleAction_Reject(actingUser, (DiscloseRequestMessage) messageEntity, requestPayload);
				default:
					throw MessageServiceException.actionNotSupported(action);
			}
		}

		private MessageActionDTO handleAction_Accept(UserDTO actingUser, DiscloseRequestMessage discloseRequestMessageEntity,
				MessageActionDTO payload)
		{
			return updateGoalConflictMessageStatus(actingUser, discloseRequestMessageEntity,
					GoalConflictMessage.Status.DISCLOSE_ACCEPTED, payload.getProperty("message"));
		}

		private MessageActionDTO handleAction_Reject(UserDTO actingUser, DiscloseRequestMessage discloseRequestMessageEntity,
				MessageActionDTO payload)
		{
			return updateGoalConflictMessageStatus(actingUser, discloseRequestMessageEntity,
					GoalConflictMessage.Status.DISCLOSE_REJECTED, payload.getProperty("message"));
		}

		private MessageActionDTO updateGoalConflictMessageStatus(UserDTO actingUser,
				DiscloseRequestMessage discloseRequestMessageEntity, Status status, String message)
		{
			GoalConflictMessage targetGoalConflictMessage = discloseRequestMessageEntity.getTargetGoalConflictMessage();
			targetGoalConflictMessage.setStatus(status);
			targetGoalConflictMessage = Message.getRepository().save(targetGoalConflictMessage);

			discloseRequestMessageEntity = updateMessageStatus(discloseRequestMessageEntity, status);

			sendResponseMessageToRequestingUser(actingUser, discloseRequestMessageEntity, message);

			return MessageActionDTO
					.createInstanceActionDone(theDTOFactory.createInstance(actingUser, discloseRequestMessageEntity));
		}

		private DiscloseRequestMessage updateMessageStatus(DiscloseRequestMessage discloseRequestMessageEntity, Status status)
		{
			discloseRequestMessageEntity.setStatus(status);
			return Message.getRepository().save(discloseRequestMessageEntity);
		}

		private void sendResponseMessageToRequestingUser(UserDTO respondingUser, DiscloseRequestMessage requestMessageEntity,
				String message)
		{
			MessageDestinationDTO messageDestination = userAnonymizedService
					.getUserAnonymized(requestMessageEntity.getRelatedUserAnonymizedID()).getAnonymousDestination();
			assert messageDestination != null;
			messageService.sendMessage(DiscloseResponseMessage.createInstance(respondingUser.getID(),
					respondingUser.getPrivateData().getUserAnonymizedID(), requestMessageEntity.getTargetGoalConflictMessageID(),
					requestMessageEntity.getStatus(), respondingUser.getPrivateData().getNickname(), message),
					messageDestination);
		}
	}
}
