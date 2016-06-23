/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.ActivityCommentMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.BuddyMessageEmbeddedUserDTO;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.MessageServiceException;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("activityCommentMessage")
public class ActivityCommentMessageDTO extends BuddyMessageEmbeddedUserDTO
{
	private static final String REPLY = "reply";

	private ActivityCommentMessageDTO(UUID id, ZonedDateTime creationTime, UserDTO user, UUID loginID, String nickname,
			String message)
	{
		super(id, creationTime, user, nickname, message);
	}

	@Override
	public String getType()
	{
		return "ActivityCommentMessage";
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		// TODO if (!isProcessed)
		{
			possibleActions.add(REPLY);
		}
		return possibleActions;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static ActivityCommentMessageDTO createInstance(UserDTO actingUser, ActivityCommentMessage messageEntity)
	{
		return new ActivityCommentMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(),
				UserDTO.createInstanceIfNotNull(messageEntity.getSenderUser()), messageEntity.getRelatedUserAnonymizedID(),
				messageEntity.getSenderNickname(), messageEntity.getMessage());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(ActivityCommentMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return ActivityCommentMessageDTO.createInstance(actingUser, (ActivityCommentMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case REPLY:
					return handleAction_Reply(actingUser, (ActivityCommentMessage) messageEntity, requestPayload);
				default:
					throw MessageServiceException.actionNotSupported(action);
			}
		}

		private MessageActionDTO handleAction_Reply(UserDTO actingUser, ActivityCommentMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			buddyService.removeBuddyAfterBuddyRemovedConnection(actingUser.getID(), messageEntity.getSenderUserID());

			messageEntity = updateMessageStatusAsProcessed(messageEntity);

			return MessageActionDTO.createInstanceActionDone(theDTOFactory.createInstance(actingUser, messageEntity));
		}

		private ActivityCommentMessage updateMessageStatusAsProcessed(ActivityCommentMessage messageEntity)
		{
			// TODO messageEntity.setProcessed();
			return Message.getRepository().save(messageEntity);
		}
	}
}
