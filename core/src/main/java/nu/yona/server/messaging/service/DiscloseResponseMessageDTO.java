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

import nu.yona.server.messaging.entities.DiscloseResponseMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("discloseResponseMessage")
public class DiscloseResponseMessageDTO extends MessageDTO
{
	private String nickname;

	private DiscloseResponseMessageDTO(UUID id, Date creationTime, String nickname)
	{
		super(id, creationTime);
		this.nickname = nickname;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		return possibleActions;
	}

	public String getNickname()
	{
		return nickname;
	}

	public static DiscloseResponseMessageDTO createInstance(UserDTO requestingUser, DiscloseResponseMessage messageEntity)
	{
		return new DiscloseResponseMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(),
				messageEntity.getNickname());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(DiscloseResponseMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO requestingUser, Message messageEntity)
		{
			return DiscloseResponseMessageDTO.createInstance(requestingUser, (DiscloseResponseMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				default:
					throw new IllegalArgumentException("Action '" + action + "' is not supported");
			}
		}
	}
}
