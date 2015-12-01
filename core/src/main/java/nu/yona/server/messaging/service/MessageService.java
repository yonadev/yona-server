/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.InvalidMessageActionException;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class MessageService
{
	@Autowired
	private UserService userService;

	@Autowired
	private TheDTOManager dtoManager;

	public Page<MessageDTO> getDirectMessages(UUID userID, Pageable pageable)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getNamedMessageSource(user);
		return wrapAllMessagesAsDTOs(user, messageSource, pageable);
	}

	public MessageDTO getDirectMessage(UUID userID, UUID messageID)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getNamedMessageSource(user);
		return dtoManager.createInstance(user, messageSource.getMessage(messageID));
	}

	public Page<MessageDTO> getAnonymousMessages(UUID userID, Pageable pageable)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getAnonymousMessageSource(user);
		return wrapAllMessagesAsDTOs(user, messageSource, pageable);
	}

	public MessageDTO getAnonymousMessage(UUID userID, UUID messageID)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.createInstance(user, messageSource.getMessage(messageID));
	}

	public MessageActionDTO handleMessageAction(UUID userID, UUID id, String action, MessageActionDTO requestPayload)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getNamedMessageSource(user);
		return dtoManager.handleAction(user, messageSource.getMessage(id), action, requestPayload);
	}

	public MessageActionDTO handleAnonymousMessageAction(UUID userID, UUID id, String action, MessageActionDTO requestPayload)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.handleAction(user, messageSource.getMessage(id), action, requestPayload);
	}

	public MessageActionDTO deleteMessage(UUID userID, UUID id)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getNamedMessageSource(user);
		Message message = messageSource.getMessage(id);
		MessageDestination destination = MessageDestination.getRepository()
				.findOne(user.getPrivateData().getNamedMessageDestinationID());
		deleteMessage(message, destination);
		return new MessageActionDTO(Collections.singletonMap("status", "done"));
	}

	public MessageActionDTO deleteAnonymousMessage(UUID userID, UUID id)
	{
		UserDTO user = userService.getPrivateUser(userID);
		user.assertMobileNumberConfirmed();
		MessageSource messageSource = getAnonymousMessageSource(user);
		Message message = messageSource.getMessage(id);
		MessageDestination destination = MessageDestination.getRepository()
				.findOne(user.getPrivateData().getAnonymousMessageDestinationID());
		deleteMessage(message, destination);
		return new MessageActionDTO(Collections.singletonMap("status", "done"));
	}

	private void deleteMessage(Message message, MessageDestination destination)
	{
		if (!message.canBeDeleted())
		{
			throw InvalidMessageActionException.unprocessedMessageCannotBeDeleted();
		}

		destination.remove(message);
		MessageDestination.getRepository().save(destination);
		Message.getRepository().delete(message);
	}

	private MessageSource getNamedMessageSource(UserDTO user)
	{
		return MessageSource.getRepository().findOne(user.getPrivateData().getNamedMessageSourceID());
	}

	private MessageSource getAnonymousMessageSource(UserDTO user)
	{
		return MessageSource.getRepository().findOne(user.getPrivateData().getAnonymousMessageSourceID());
	}

	private Page<MessageDTO> wrapAllMessagesAsDTOs(UserDTO user, MessageSource messageSource, Pageable pageable)
	{
		return wrapMessagesAsDTOs(user, messageSource.getMessages(pageable), pageable);
	}

	private Page<MessageDTO> wrapMessagesAsDTOs(UserDTO user, Page<Message> messageEntities, Pageable pageable)
	{
		List<MessageDTO> allMessagePayloads = messageEntities.getContent().stream().map(m -> dtoManager.createInstance(user, m))
				.collect(Collectors.toList());
		return new PageImpl<MessageDTO>(allMessagePayloads, pageable, messageEntities.getTotalElements());
	}

	public static interface DTOManager
	{
		MessageDTO createInstance(UserDTO user, Message messageEntity);

		MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action, MessageActionDTO requestPayload);
	}

	@Component
	public static class TheDTOManager implements DTOManager
	{
		private Map<Class<? extends Message>, DTOManager> managers = new HashMap<>();

		@Override
		public MessageDTO createInstance(UserDTO user, Message messageEntity)
		{
			return getManager(messageEntity).createInstance(user, messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO user, Message messageEntity, String action, MessageActionDTO requestPayload)
		{
			return getManager(messageEntity).handleAction(user, messageEntity, action, requestPayload);
		}

		public void addManager(Class<? extends Message> messageEntityClass, DTOManager manager)
		{
			DTOManager previousManager = managers.put(messageEntityClass, manager);
			assert previousManager == null;
		}

		private DTOManager getManager(Message messageEntity)
		{
			assert messageEntity != null;

			for (Class<? extends Message> messageClass : managers.keySet())
			{
				if (messageClass.isInstance(messageEntity))
				{
					return managers.get(messageClass);
				}
			}
			throw new IllegalArgumentException("No DTO manager registered for class " + messageEntity.getClass());
		}
	}
}
