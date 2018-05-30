/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.exceptions.InvalidMessageActionException;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.LockPool;
import nu.yona.server.util.Require;
import nu.yona.server.util.TransactionHelper;

@Service
public class MessageService
{
	@Autowired
	private UserService userService;

	@Autowired
	private LockPool<UUID> userSynchronizer;
	@Autowired
	private TransactionHelper transactionHelper;

	@Autowired
	private BuddyService buddyService;

	@Autowired
	private TheDtoManager dtoManager;

	@Autowired
	private MessageSourceRepository messageSourceRepository;

	@Transactional
	public Page<MessageDto> getReceivedMessages(UserDto user, boolean onlyUnreadMessages, Pageable pageable)
	{
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

	// This is intentionally not marked with @Transactional, as the transaction is explicitly started inside this method
	public UserDto prepareMessageCollection(UUID userId)
	{
		User user = userService.getPrivateValidatedUserEntity(userId);
		boolean updated = false;
		if (mustTransferDirectMessagesToAnonymousDestination(user))
		{
			doInTransactionWithLockOnUser(user, this::transferDirectMessagesToAnonymousDestination);
			updated = true;
		}
		if (mustProcessUnprocessedMessages(user))
		{
			doInTransactionWithLockOnUser(user, this::processUnprocessedMessages);
			updated = true;
		}
		if (updated)
		{
			return transactionHelper.executeInNewTransaction(() -> userService.getPrivateValidatedUser(userId));
		}
		return userService.createUserDtoWithPrivateData(user);
	}

	private void doInTransactionWithLockOnUser(User user, Consumer<User> job)
	{
		try (LockPool<UUID>.Lock lock = userSynchronizer.lock(user.getId()))
		{
			transactionHelper.executeInNewTransaction(() -> job.accept(user));
		}
	}

	private boolean mustTransferDirectMessagesToAnonymousDestination(User user)
	{
		return getNamedMessageSource(user).getMessages(null).hasContent();
	}

	@Transactional
	private void transferDirectMessagesToAnonymousDestination(User user)
	{
		MessageSource directMessageSource = getNamedMessageSource(user);
		Page<Message> directMessages = directMessageSource.getMessages(null);
		MessageSource anonymousMessageSource = getAnonymousMessageSource(user);
		MessageDestination anonymousMessageDestination = anonymousMessageSource.getDestination();
		MessageDestination directMessageDestination = directMessageSource.getDestination();
		for (Message directMessage : directMessages)
		{
			directMessageDestination.remove(directMessage);
			anonymousMessageDestination.send(directMessage);
		}
		MessageDestination.getRepository().save(directMessageDestination);
		MessageDestination.getRepository().save(anonymousMessageDestination);
	}

	private boolean mustProcessUnprocessedMessages(User user)
	{
		return !getUnprocessedMessages(user).isEmpty();
	}

	private List<Long> getUnprocessedMessages(User user)
	{
		MessageDestination anonymousMessageDestination = getAnonymousMessageSource(user).getDestination();
		return Message.getRepository().findUnprocessedMessagesFromDestination(anonymousMessageDestination.getId());
	}

	@Transactional
	private void processUnprocessedMessages(User user)
	{
		List<Long> idsOfUnprocessedMessages = getUnprocessedMessages(user);

		UserDto userDto = userService.getPrivateUser(user.getId());
		MessageActionDto emptyPayload = new MessageActionDto(Collections.emptyMap());
		for (long id : idsOfUnprocessedMessages)
		{
			handleMessageAction(userDto, id, "process", emptyPayload);
		}
	}

	@Transactional
	public MessageDto getMessage(UserDto user, long messageId)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.createInstance(user, messageSource.getMessage(messageId));
	}

	@Transactional
	public MessageActionDto handleMessageAction(UserDto user, long id, String action, MessageActionDto requestPayload)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.handleAction(user, messageSource.getMessage(id), action, requestPayload);
	}

	@Transactional
	public MessageActionDto deleteMessage(UserDto user, long id)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		Message message = messageSource.getMessage(id);

		deleteMessage(user, message);

		return MessageActionDto.createInstanceActionDone();
	}

	private void deleteMessage(UserDto user, Message message)
	{
		MessageDto messageDto = dtoManager.createInstance(user, message);
		Require.that(messageDto.canBeDeleted(), InvalidMessageActionException::unprocessedMessageCannotBeDeleted);

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

	private MessageSource getNamedMessageSource(User user)
	{
		return getMessageSource(user.getNamedMessageSourceId());
	}

	private MessageSource getAnonymousMessageSource(UserDto user)
	{
		return getMessageSource(user.getOwnPrivateData().getAnonymousMessageSourceId());
	}

	private MessageSource getAnonymousMessageSource(User user)
	{
		return getMessageSource(user.getAnonymousMessageSourceId());
	}

	private MessageSource getMessageSource(UUID id)
	{
		return messageSourceRepository.findOne(id);
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
