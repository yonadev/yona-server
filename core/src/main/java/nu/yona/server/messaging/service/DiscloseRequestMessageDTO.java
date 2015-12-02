/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Collections;
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
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("discloseRequestMessage")
public class DiscloseRequestMessageDTO extends MessageDTO
{
	private static final String ACCEPT = "accept";
	private static final String REJECT = "reject";

	private String nickname;
	private boolean isAccepted;
	private boolean isRejected;

	private GoalConflictMessageDTO targetGoalConflictMessage;

	private DiscloseRequestMessageDTO(UUID id, Date creationTime, String nickname, boolean isAccepted, boolean isRejected,
			UUID targetGoalConflictMessageOriginID, GoalConflictMessageDTO targetGoalConflictMessage)
	{
		super(id, creationTime, targetGoalConflictMessageOriginID);
		this.nickname = nickname;
		this.isAccepted = isAccepted;
		this.isRejected = isRejected;
		this.targetGoalConflictMessage = targetGoalConflictMessage;
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

	public GoalConflictMessageDTO getTargetGoalConflictMessage()
	{
		return targetGoalConflictMessage;
	}

	public boolean isAccepted()
	{
		return isAccepted;
	}

	public boolean isRejected()
	{
		return isRejected;
	}

	public static DiscloseRequestMessageDTO createInstance(UserDTO requestingUser, DiscloseRequestMessage messageEntity)
	{
		GoalConflictMessage targetGoalConflictMessage = messageEntity.getTargetGoalConflictMessage();
		return new DiscloseRequestMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), messageEntity.getNickname(),
				messageEntity.isAccepted(), messageEntity.isRejected(),
				targetGoalConflictMessage.getOriginGoalConflictMessageID(),
				GoalConflictMessageDTO.createInstance(targetGoalConflictMessage, null));
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

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

		private MessageActionDTO handleAction_Accept(UserDTO acceptingUser, DiscloseRequestMessage discloseRequestMessageEntity,
				MessageActionDTO payload)
		{
			return updateResult(acceptingUser, discloseRequestMessageEntity, GoalConflictMessage.Status.DISCLOSE_ACCEPTED,
					payload.getProperty("message"));
		}

		private MessageActionDTO handleAction_Reject(UserDTO rejectingUser, DiscloseRequestMessage discloseRequestMessageEntity,
				MessageActionDTO payload)
		{
			return updateResult(rejectingUser, discloseRequestMessageEntity, GoalConflictMessage.Status.DISCLOSE_REJECTED,
					payload.getProperty("message"));
		}

		private MessageActionDTO updateResult(UserDTO respondingUser, DiscloseRequestMessage discloseRequestMessageEntity,
				Status status, String message)
		{
			GoalConflictMessage targetGoalConflictMessage = discloseRequestMessageEntity.getTargetGoalConflictMessage();
			targetGoalConflictMessage.setStatus(status);
			Message.getRepository().save(targetGoalConflictMessage);

			discloseRequestMessageEntity.setStatus(status);
			Message.getRepository().save(discloseRequestMessageEntity);

			sendResponseMessageToRequestingUser(respondingUser, discloseRequestMessageEntity, message);

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private void sendResponseMessageToRequestingUser(UserDTO respondingUser, DiscloseRequestMessage requestMessageEntity,
				String message)
		{
			UserAnonymized userAnonymized = UserAnonymized.getRepository().findOne(requestMessageEntity.getRelatedVPNLoginID());
			MessageDestination messageDestination = userAnonymized.getAnonymousDestination();
			assert messageDestination != null;
			messageDestination.send(DiscloseResponseMessage.createInstance(respondingUser.getID(),
					respondingUser.getPrivateData().getVpnProfile().getVPNLoginID(),
					requestMessageEntity.getTargetGoalConflictMessageID(), requestMessageEntity.getStatus(),
					respondingUser.getPrivateData().getNickName(), message));
			MessageDestination.getRepository().save(messageDestination);
		}
	}
}
