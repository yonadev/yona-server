/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
	private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

	@Autowired
	private UserService userService;

	@Autowired
	private TheDTOManager dtoManager;

	@Transactional
	public Page<MessageDTO> getReceivedMessages(UUID userID, boolean onlyUnreadMessages, Pageable pageable)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);
		return wrapMessagesAsDTOs(user, getReceivedMessageEntities(user, onlyUnreadMessages, pageable), pageable);
	}

	@Transactional
	public Page<Message> getReceivedMessageEntities(UUID userID, Pageable pageable)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);

		return getReceivedMessageEntities(user, false, pageable);
	}

	private Page<Message> getReceivedMessageEntities(UserDTO user, boolean onlyUnreadMessages, Pageable pageable)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		return messageSource.getReceivedMessages(pageable, onlyUnreadMessages);
	}

	public Page<Message> getReceivedMessageEntitiesSinceDate(UUID userID, LocalDateTime earliestDateTime, Pageable pageable)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);
		MessageSource messageSource = getAnonymousMessageSource(user);
		return messageSource.getReceivedMessages(pageable, earliestDateTime);
	}

	// handle in a separate transaction to limit exceptions caused by concurrent calls to this method
	@Transactional
	public void transferDirectMessagesToAnonymousDestination(UUID userID)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);
		try
		{
			tryTransferDirectMessagesToAnonymousDestination(user);
		}
		catch (DataIntegrityViolationException e)
		{
			if (e.getCause() instanceof ConstraintViolationException)
			{
				// Ignore and proceed. Another concurrent thread has transferred the messages.
				// We avoid a lock here because that limits scaling this service horizontally.
				logger.info(
						"The direct messages of user with mobile number '" + user.getMobileNumber() + "' and ID '" + user.getID()
								+ "' were apparently concurrently moved to the anonymous messages while handling another request.",
						e);
			}
			else
			{
				throw e;
			}
		}
	}

	private void tryTransferDirectMessagesToAnonymousDestination(UserDTO user)
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

	@Transactional
	public MessageDTO getMessage(UUID userID, UUID messageID)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);

		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.createInstance(user, messageSource.getMessage(messageID));
	}

	@Transactional
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

	private Page<MessageDTO> wrapMessagesAsDTOs(UserDTO user, Page<? extends Message> messageEntities, Pageable pageable)
	{
		List<MessageDTO> allMessagePayloads = wrapMessagesAsDTOs(user, messageEntities.getContent());
		return new PageImpl<MessageDTO>(allMessagePayloads, pageable, messageEntities.getTotalElements());
	}

	private List<MessageDTO> wrapMessagesAsDTOs(UserDTO user, List<? extends Message> messageEntities)
	{
		return messageEntities.stream().map(m -> messageToDTO(user, m)).collect(Collectors.toList());
	}

	public MessageDTO messageToDTO(UserDTO user, Message message)
	{
		return dtoManager.createInstance(user, message);
	}

	public static interface DTOManager
	{
		MessageDTO createInstance(UserDTO actingUser, Message messageEntity);

		MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action, MessageActionDTO requestPayload);
	}

	@Component
	public static class TheDTOManager implements DTOManager
	{
		private final Map<Class<? extends Message>, DTOManager> managers = new HashMap<>();

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

	@Transactional
	public void sendMessageToUserAnonymized(UserAnonymizedDTO userAnonymized, Message message)
	{
		sendMessage(message, userAnonymized.getAnonymousDestination());
	}

	@Transactional
	public Page<MessageDTO> getActivityRelatedMessages(UUID userID, UUID activityID, Pageable pageable)
	{
		UserDTO user = userService.getPrivateValidatedUser(userID);
		MessageSource messageSource = getAnonymousMessageSource(user);
		return wrapMessagesAsDTOs(user, messageSource.getActivityRelatedMessages(activityID, pageable), pageable);
	}
}