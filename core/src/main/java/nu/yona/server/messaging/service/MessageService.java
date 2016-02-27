/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

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
import nu.yona.server.subscriptions.service.UserAnonymizedDTO;
import nu.yona.server.subscriptions.service.UserDTO;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class MessageService
{
	@Autowired
	private UserService userService;

	@Autowired
	private TheDTOManager dtoManager;

	@Transactional
	public Page<MessageDTO> getMessages(UUID userID, Pageable pageable)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);

		transferDirectMessagesToAnonymousDestination(user);

		MessageSource messageSource = getAnonymousMessageSource(user);
		return wrapAllMessagesAsDTOs(user, messageSource, pageable);
	}

	public MessageDTO getMessage(UUID userID, UUID messageID)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);

		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.createInstance(user, messageSource.getMessage(messageID));
	}

	public MessageActionDTO handleMessageAction(UUID userID, UUID id, String action, MessageActionDTO requestPayload)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);

		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.handleAction(user, messageSource.getMessage(id), action, requestPayload);
	}

	@Transactional
	public MessageActionDTO deleteMessage(UUID userID, UUID id)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);

		MessageSource messageSource = getAnonymousMessageSource(user);
		Message message = messageSource.getMessage(id);

		deleteMessage(user, message, messageSource.getDestination());

		return MessageActionDTO.createInstanceActionDone();
	}

	private void transferDirectMessagesToAnonymousDestination(UserDTO user)
	{
		MessageSource directMessageSource = getNamedMessageSource(user);
		MessageDestination directMessageDestination = directMessageSource.getDestination();
		Page<Message> directMessages = directMessageSource.getMessages(null);
	
		MessageSource anonymousMessageSource = getAnonymousMessageSource(user);
		MessageDestination anonymousMessageDestination = anonymousMessageSource.getDestination();
		for (Message directMessage : directMessages)
		{
			directMessageDestination.remove(directMessage);
			anonymousMessageDestination.send(directMessage);
		}
		MessageDestination.getRepository().save(directMessageDestination);
		MessageDestination.getRepository().save(anonymousMessageDestination);
	}

	private void deleteMessage(UserDTO user, Message message, MessageDestination destination)
	{
		MessageDTO messageDTO = dtoManager.createInstance(user, message);
		if (!messageDTO.canBeDeleted())
		{
			throw InvalidMessageActionException.unprocessedMessageCannotBeDeleted();
		}

		destination.remove(message);
		MessageDestination.getRepository().save(destination);
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
		MessageDTO createInstance(UserDTO actingUser, Message messageEntity);

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

			throw MessageServiceException.noDtoManagerRegistered(messageEntity.getClass());
		}
	}

	@Transactional
	public void sendMessage(Message message, MessageDestinationDTO destination)
	{
		MessageDestination destinationEntity = MessageDestination.getRepository().findOne(destination.getID());
		destinationEntity.send(message);
		MessageDestination.getRepository().save(destinationEntity);
	}

	@Transactional
	public void removeMessagesFromUser(MessageDestinationDTO destination, UUID sentByUserAnonymizedID)
	{
		if (sentByUserAnonymizedID == null)
		{
			throw new IllegalArgumentException("sentByUserAnonymizedID cannot be null");
		}

		MessageDestination destinationEntity = MessageDestination.getRepository().findOne(destination.getID());
		destinationEntity.removeMessagesFromUser(sentByUserAnonymizedID);
		MessageDestination.getRepository().save(destinationEntity);
	}

	@Transactional
	public void broadcastMessageToBuddies(UserAnonymizedDTO userAnonymized, Supplier<Message> messageSupplier)
	{
		userAnonymized.getBuddyDestinations().stream().forEach(destination -> sendMessage(messageSupplier.get(), destination));
	}
}
