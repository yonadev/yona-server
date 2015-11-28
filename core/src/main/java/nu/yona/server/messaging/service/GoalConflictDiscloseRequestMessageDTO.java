/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.messaging.entities.GoalConflictDiscloseRequestMessage;
import nu.yona.server.messaging.entities.GoalConflictDiscloseResponseMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("goalConflictDiscloseRequestMessage")
public class GoalConflictDiscloseRequestMessageDTO extends MessageDTO
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private String nickname;
	private boolean isAccepted;
	private boolean isRejected;

	private GoalConflictDiscloseRequestMessageDTO(UUID id, String nickname, boolean isAccepted, boolean isRejected)
	{
		super(id);
		this.nickname = nickname;
		this.isAccepted = isAccepted;
		this.isRejected = isRejected;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (!isAccepted && !isRejected)
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

	public boolean isAccepted()
	{
		return isAccepted;
	}

	public boolean isRejected()
	{
		return isRejected;
	}

	public static GoalConflictDiscloseRequestMessageDTO createInstance(UserDTO requestingUser,
			GoalConflictDiscloseRequestMessage messageEntity)
	{
		return new GoalConflictDiscloseRequestMessageDTO(messageEntity.getID(), messageEntity.getNickname(),
				messageEntity.isAccepted(), messageEntity.isRejected());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(GoalConflictDiscloseRequestMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return GoalConflictDiscloseRequestMessageDTO.createInstance(actingUser,
					(GoalConflictDiscloseRequestMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case ACCEPT:
					return handleAction_Accept(actingUser, (GoalConflictDiscloseRequestMessage) messageEntity, requestPayload);
				case REJECT:
					return handleAction_Reject(actingUser, (GoalConflictDiscloseRequestMessage) messageEntity, requestPayload);
				default:
					throw new IllegalArgumentException("Action '" + action + "' is not supported");
			}
		}

		private MessageActionDTO handleAction_Accept(UserDTO acceptingUser,
				GoalConflictDiscloseRequestMessage discloseRequestMessageEntity, MessageActionDTO payload)
		{
			return updateResult(acceptingUser, discloseRequestMessageEntity, GoalConflictMessage.Status.DISCLOSE_ACCEPTED);
		}

		private MessageActionDTO handleAction_Reject(UserDTO rejectingUser,
				GoalConflictDiscloseRequestMessage discloseRequestMessageEntity, MessageActionDTO payload)
		{
			return updateResult(rejectingUser, discloseRequestMessageEntity, GoalConflictMessage.Status.DISCLOSE_REJECTED);
		}

		private MessageActionDTO updateResult(UserDTO respondingUser,
				GoalConflictDiscloseRequestMessage discloseRequestMessageEntity, Status status)
		{
			GoalConflictMessage targetGoalConflictMessage = discloseRequestMessageEntity.getTargetGoalConflictMessage();
			targetGoalConflictMessage.setStatus(status);
			Message.getRepository().save(targetGoalConflictMessage);

			discloseRequestMessageEntity.setStatus(status);
			Message.getRepository().save(discloseRequestMessageEntity);

			sendResponseMessageToRequestingUser(respondingUser, discloseRequestMessageEntity);

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private void sendResponseMessageToRequestingUser(UserDTO respondingUser,
				GoalConflictDiscloseRequestMessage requestMessageEntity)
		{
			MessageDestination messageDestination = requestMessageEntity.getUser().getAnonymousDestination();
			assert messageDestination != null;
			messageDestination.send(GoalConflictDiscloseResponseMessage.createInstance(
					respondingUser.getPrivateData().getVpnProfile().getVPNLoginID(),
					requestMessageEntity.getTargetGoalConflictMessageID(), requestMessageEntity.getStatus(),
					respondingUser.getPrivateData().getNickName()));
			MessageDestination.getRepository().save(messageDestination);
		}
	}
}
