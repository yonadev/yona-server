/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.messaging.entities.DisclosureRequestMessage;
import nu.yona.server.messaging.entities.DisclosureResponseMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("disclosureRequestMessage")
public class DisclosureRequestMessageDTO extends BuddyMessageLinkedUserDTO
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private Status status;

	private DisclosureRequestMessageDTO(UUID id, ZonedDateTime creationTime, UserDTO user, String nickname, String message,
			Status status, UUID targetGoalConflictMessageID)
	{
		super(id, creationTime, targetGoalConflictMessageID, user, nickname, message);
		this.status = status;
	}

	@Override
	public String getType()
	{
		return "DisclosureRequestMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (status == Status.DISCLOSURE_REQUESTED)
		{
			possibleActions.add(ACCEPT);
			possibleActions.add(REJECT);
		}
		return possibleActions;
	}

	public Status getStatus()
	{
		return status;
	}

	@Override
	public boolean canBeDeleted()
	{
		return this.status == Status.DISCLOSURE_ACCEPTED || this.status == Status.DISCLOSURE_REJECTED;
	}

	public static DisclosureRequestMessageDTO createInstance(UserDTO actingUser, DisclosureRequestMessage messageEntity)
	{
		GoalConflictMessage targetGoalConflictMessage = messageEntity.getTargetGoalConflictMessage();
		return new DisclosureRequestMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(),
				UserDTO.createInstanceIfNotNull(messageEntity.getUser()), messageEntity.getNickname(), messageEntity.getMessage(),
				messageEntity.getStatus(), targetGoalConflictMessage.getOriginGoalConflictMessageID());
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
			theDTOFactory.addManager(DisclosureRequestMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return DisclosureRequestMessageDTO.createInstance(actingUser, (DisclosureRequestMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case ACCEPT:
					return handleAction_Accept(actingUser, (DisclosureRequestMessage) messageEntity, requestPayload);
				case REJECT:
					return handleAction_Reject(actingUser, (DisclosureRequestMessage) messageEntity, requestPayload);
				default:
					throw MessageServiceException.actionNotSupported(action);
			}
		}

		private MessageActionDTO handleAction_Accept(UserDTO actingUser, DisclosureRequestMessage disclosureRequestMessageEntity,
				MessageActionDTO payload)
		{
			return updateGoalConflictMessageStatus(actingUser, disclosureRequestMessageEntity,
					GoalConflictMessage.Status.DISCLOSURE_ACCEPTED, payload.getProperty("message"));
		}

		private MessageActionDTO handleAction_Reject(UserDTO actingUser, DisclosureRequestMessage disclosureRequestMessageEntity,
				MessageActionDTO payload)
		{
			return updateGoalConflictMessageStatus(actingUser, disclosureRequestMessageEntity,
					GoalConflictMessage.Status.DISCLOSURE_REJECTED, payload.getProperty("message"));
		}

		private MessageActionDTO updateGoalConflictMessageStatus(UserDTO actingUser,
				DisclosureRequestMessage disclosureRequestMessageEntity, Status status, String message)
		{
			GoalConflictMessage targetGoalConflictMessage = disclosureRequestMessageEntity.getTargetGoalConflictMessage();
			targetGoalConflictMessage.setStatus(status);
			targetGoalConflictMessage = Message.getRepository().save(targetGoalConflictMessage);

			disclosureRequestMessageEntity = updateMessageStatus(disclosureRequestMessageEntity, status);

			sendResponseMessageToRequestingUser(actingUser, disclosureRequestMessageEntity, message);

			return MessageActionDTO
					.createInstanceActionDone(theDTOFactory.createInstance(actingUser, disclosureRequestMessageEntity));
		}

		private DisclosureRequestMessage updateMessageStatus(DisclosureRequestMessage disclosureRequestMessageEntity,
				Status status)
		{
			disclosureRequestMessageEntity.setStatus(status);
			return Message.getRepository().save(disclosureRequestMessageEntity);
		}

		private void sendResponseMessageToRequestingUser(UserDTO respondingUser, DisclosureRequestMessage requestMessageEntity,
				String message)
		{
			MessageDestinationDTO messageDestination = userAnonymizedService
					.getUserAnonymized(requestMessageEntity.getRelatedUserAnonymizedID()).getAnonymousDestination();
			assert messageDestination != null;
			messageService.sendMessage(DisclosureResponseMessage.createInstance(respondingUser.getID(),
					respondingUser.getPrivateData().getUserAnonymizedID(), requestMessageEntity.getTargetGoalConflictMessageID(),
					requestMessageEntity.getStatus(), respondingUser.getPrivateData().getNickname(), message),
					messageDestination);
		}
	}
}
