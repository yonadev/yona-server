/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.batch.client.BatchProxyService;
import nu.yona.server.exceptions.InvalidMessageActionException;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class MessageService
{
	private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

	@Autowired
	private UserService userService;

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private BatchProxyService batchProxyService;

	@Autowired
	private TheDtoManager dtoManager;

	@Transactional
	public Page<MessageDto> getReceivedMessages(UUID userId, boolean onlyUnreadMessages, Pageable pageable)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);
		return wrapMessagesAsDtos(user, getReceivedMessageEntities(user, onlyUnreadMessages, pageable), pageable);
	}

	@Transactional
	public Page<Message> getReceivedMessageEntities(UUID userId, Pageable pageable)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);

		return getReceivedMessageEntities(user, false, pageable);
	}

	private Page<Message> getReceivedMessageEntities(UserDto user, boolean onlyUnreadMessages, Pageable pageable)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		return messageSource.getReceivedMessages(pageable, onlyUnreadMessages);
	}

	public Page<Message> getReceivedMessageEntitiesSinceDate(UUID userId, LocalDateTime earliestDateTime, Pageable pageable)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);
		MessageSource messageSource = getAnonymousMessageSource(user);
		return messageSource.getReceivedMessages(pageable, earliestDateTime);
	}

	// handle in a separate transaction to limit exceptions caused by concurrent calls to this method
	@Transactional
	public void prepareMessageCollection(UUID userId)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);
		try
		{
			tryTransferDirectMessagesToAnonymousDestination(user);
			tryProcessUnprocessedMessages(user);
		}
		catch (DataIntegrityViolationException e)
		{
			if (e.getCause() instanceof ConstraintViolationException)
			{
				// Ignore and proceed. Another concurrent thread has transferred the messages.
				// We avoid a lock here because that limits scaling this service horizontally.
				logger.info(
						"The direct messages of user with mobile number '" + user.getMobileNumber() + "' and ID '" + user.getId()
								+ "' were apparently concurrently moved to the anonymous messages while handling another request.",
						e);
			}
			else
			{
				throw e;
			}
		}
	}

	private void tryTransferDirectMessagesToAnonymousDestination(UserDto user)
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

	private void tryProcessUnprocessedMessages(UserDto user)
	{
		MessageDestination anonymousMessageDestination = getAnonymousMessageSource(user).getDestination();
		List<Long> idsOfUnprocessedMessages = Message.getRepository()
				.findUnprocessedMessagesFromDestination(anonymousMessageDestination.getId());

		MessageActionDto emptyPayload = new MessageActionDto(Collections.emptyMap());
		for (long id : idsOfUnprocessedMessages)
		{
			handleMessageAction(user.getId(), id, "process", emptyPayload);
		}
	}

	@Transactional
	public MessageDto getMessage(UUID userId, long messageId)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);

		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.createInstance(user, messageSource.getMessage(messageId));
	}

	@Transactional
	public MessageActionDto handleMessageAction(UUID userId, long id, String action, MessageActionDto requestPayload)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);

		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.handleAction(user, messageSource.getMessage(id), action, requestPayload);
	}

	@Transactional
	public MessageActionDto deleteMessage(UUID userId, long id)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);

		MessageSource messageSource = getAnonymousMessageSource(user);
		Message message = messageSource.getMessage(id);

		deleteMessage(user, message);

		return MessageActionDto.createInstanceActionDone();
	}

	private void deleteMessage(UserDto user, Message message)
	{
		MessageDto messageDto = dtoManager.createInstance(user, message);
		if (!messageDto.canBeDeleted())
		{
			throw InvalidMessageActionException.unprocessedMessageCannotBeDeleted();
		}

		deleteMessages(Collections.singleton(message));
	}

	private void deleteMessages(Collection<Message> messages)
	{
		Set<Message> messagesToBeDeleted = messages.stream().flatMap(m -> m.getMessagesToBeCascadinglyDeleted().stream())
				.collect(Collectors.toSet());
		messagesToBeDeleted.addAll(messages);
		Set<MessageDestination> involvedMessageDestinations = messagesToBeDeleted.stream().map(Message::getMessageDestination)
				.collect(Collectors.toSet());

		messagesToBeDeleted.forEach(Message::prepareForDelete);
		involvedMessageDestinations.forEach(d -> MessageDestination.getRepository().saveAndFlush(d));

		messagesToBeDeleted.forEach(m -> m.getMessageDestination().remove(m));
		involvedMessageDestinations.forEach(d -> MessageDestination.getRepository().save(d));
	}

	private MessageSource getNamedMessageSource(UserDto user)
	{
		return MessageSource.getRepository().findOne(user.getPrivateData().getNamedMessageSourceId());
	}

	private MessageSource getAnonymousMessageSource(UserDto user)
	{
		return MessageSource.getRepository().findOne(user.getPrivateData().getAnonymousMessageSourceId());
	}

	private Page<MessageDto> wrapMessagesAsDtos(UserDto user, Page<? extends Message> messageEntities, Pageable pageable)
	{
		List<MessageDto> allMessagePayloads = wrapMessagesAsDtos(user, messageEntities.getContent());
		return new PageImpl<>(allMessagePayloads, pageable, messageEntities.getTotalElements());
	}

	private List<MessageDto> wrapMessagesAsDtos(UserDto user, List<? extends Message> messageEntities)
	{
		return messageEntities.stream().map(m -> messageToDto(user, m)).collect(Collectors.toList());
	}

	public MessageDto messageToDto(UserDto user, Message message)
	{
		return dtoManager.createInstance(user, message);
	}

	public static interface DtoManager
	{
		MessageDto createInstance(UserDto actingUser, Message messageEntity);

		MessageActionDto handleAction(UserDto actingUser, Message messageEntity, String action, MessageActionDto requestPayload);
	}

	@Component
	public static class TheDtoManager implements DtoManager
	{
		private final Map<Class<? extends Message>, DtoManager> managers = new HashMap<>();

		@Override
		public MessageDto createInstance(UserDto user, Message messageEntity)
		{
			return getManager(messageEntity).createInstance(user, messageEntity);
		}

		@Override
		public MessageActionDto handleAction(UserDto user, Message messageEntity, String action, MessageActionDto requestPayload)
		{
			return getManager(messageEntity).handleAction(user, messageEntity, action, requestPayload);
		}

		public void addManager(Class<? extends Message> messageEntityClass, DtoManager manager)
		{
			DtoManager previousManager = managers.put(messageEntityClass, manager);
			assert previousManager == null;
		}

		private DtoManager getManager(Message messageEntity)
		{
			assert messageEntity != null;

			return managers.entrySet().stream().filter(e -> e.getKey().isInstance(messageEntity)).findAny()
					.map(Map.Entry::getValue)
					.orElseThrow(() -> MessageServiceException.noDtoManagerRegistered(messageEntity.getClass()));
		}
	}

	@Transactional
	public void sendMessageAndFlushToDatabase(Message message, MessageDestinationDto destination)
	{
		MessageDestination destinationEntity = MessageDestination.getRepository().findOne(destination.getId());
		destinationEntity.send(message);
		MessageDestination.getRepository().saveAndFlush(destinationEntity);
	}

	@Transactional
	public void sendMessage(Message message, MessageDestination destinationEntity)
	{
		destinationEntity.send(message);
	}

	@Transactional
	public void broadcastSystemMessage(String messageText)
	{
		batchProxyService.sendSystemMessage(messageText);
	}

	@Transactional
	public void removeMessagesFromUser(MessageDestinationDto destination, UUID sentByUserAnonymizedId)
	{
		if (sentByUserAnonymizedId == null)
		{
			throw new IllegalArgumentException("sentByUserAnonymizedId cannot be null");
		}

		MessageDestination destinationEntity = MessageDestination.getRepository().findOne(destination.getId());
		deleteMessages(destinationEntity.getMessagesFromUser(sentByUserAnonymizedId));
	}

	@Transactional
	public void broadcastMessageToBuddies(UserAnonymizedDto userAnonymized, Supplier<Message> messageSupplier)
	{
		buddyService.getBuddyDestinations(userAnonymized)
				.forEach(destination -> sendMessageAndFlushToDatabase(messageSupplier.get(), destination));
	}

	@Transactional
	public void sendMessageToUserAnonymized(UserAnonymizedDto userAnonymized, Message message)
	{
		sendMessageAndFlushToDatabase(message, userAnonymized.getAnonymousDestination());
	}

	@Transactional
	public Page<MessageDto> getActivityRelatedMessages(UUID userId, IntervalActivity intervalActivityEntity, Pageable pageable)
	{
		UserDto user = userService.getPrivateValidatedUser(userId);
		MessageSource messageSource = getAnonymousMessageSource(user);
		return wrapMessagesAsDtos(user, messageSource.getActivityRelatedMessages(intervalActivityEntity, pageable), pageable);
	}

	public void deleteMessagesForIntervalActivities(Collection<IntervalActivity> intervalActivities)
	{
		if (intervalActivities.isEmpty())
		{
			return;
		}
		Message.getRepository().deleteMessagesForIntervalActivities(intervalActivities);
	}
}
