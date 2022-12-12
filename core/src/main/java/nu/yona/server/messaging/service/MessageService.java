/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import nu.yona.server.LocaleContextHelper;
import nu.yona.server.analysis.entities.IntervalActivity;
import nu.yona.server.device.service.DeviceAnonymizedDto;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.InvalidMessageActionException;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.entities.MessageDestinationRepository;
import nu.yona.server.messaging.entities.MessageRepository;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.sms.SmsService;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDto;
import nu.yona.server.subscriptions.service.UserService;
import nu.yona.server.util.Require;

@Service
public class MessageService
{
	@Autowired(required = false)
	private UserService userService;

	@Autowired(required = false)
	private UserAnonymizedService userAnonymizedService;

	@Autowired(required = false)
	@Lazy
	private BuddyService buddyService;

	@Autowired(required = false)
	private TheDtoManager dtoManager;

	@Autowired(required = false)
	private MessageSourceRepository messageSourceRepository;

	@Autowired(required = false)
	private MessageDestinationRepository messageDestinationRepository;

	@Autowired(required = false)
	private MessageRepository messageRepository;

	@Autowired(required = false)
	private FirebaseService firebaseService;

	@Autowired(required = false)
	private SmsService smsService;

	@Transactional
	public Page<MessageDto> getReceivedMessages(User user, boolean onlyUnreadMessages, Pageable pageable)
	{
		return wrapMessagesAsDtos(user, getReceivedMessageEntities(userService.createUserDto(user), onlyUnreadMessages, pageable),
				pageable);
	}

	@Transactional
	public Page<Message> getReceivedMessageEntities(UUID userId, Pageable pageable)
	{
		UserDto user = userService.getValidatedUser(userId);

		return getReceivedMessageEntities(user, false, pageable);
	}

	private Page<Message> getReceivedMessageEntities(UserDto user, boolean onlyUnreadMessages, Pageable pageable)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		return messageSource.getReceivedMessages(pageable, onlyUnreadMessages);
	}

	public Page<Message> getReceivedMessageEntitiesSinceDate(UUID userId, LocalDateTime earliestDateTime, Pageable pageable)
	{
		UserDto user = userService.getValidatedUser(userId);
		MessageSource messageSource = getAnonymousMessageSource(user);
		return messageSource.getReceivedMessages(pageable, earliestDateTime);
	}

	@Transactional
	public boolean prepareMessageCollection(User user)
	{
		boolean updated = false;
		if (mustTransferDirectMessagesToAnonymousDestination(user))
		{
			transferDirectMessagesToAnonymousDestination(user);
			updated = true;
		}
		if (mustProcessUnprocessedMessages(user))
		{
			processUnprocessedMessages(user);
			updated = true;
		}
		return updated;
	}

	private boolean mustTransferDirectMessagesToAnonymousDestination(User user)
	{
		return getNamedMessageSource(user).getMessages(null).hasContent();
	}

	private void transferDirectMessagesToAnonymousDestination(User user)
	{
		MessageSource directMessageSource = getNamedMessageSource(user);
		Page<Message> directMessages = directMessageSource.getMessages(null);
		MessageSource anonymousMessageSource = getAnonymousMessageSource(user);
		MessageDestination anonymousMessageDestination = anonymousMessageSource.getDestination();
		MessageDestination directMessageDestination = directMessageSource.getDestination();
		UserAnonymizedDto userAnonymized = userAnonymizedService.getUserAnonymized(user.getUserAnonymizedId());

		directMessages.forEach(directMessageDestination::remove);
		messageDestinationRepository.saveAndFlush(directMessageDestination);

		directMessages.forEach(m -> sendMessage(userAnonymized, m.duplicate(), anonymousMessageDestination, true));
	}

	private void sendMessage(UserAnonymizedDto userAnonymized, Message message, MessageDestination destination,
			boolean sendNotification)
	{
		destination.send(message);
		messageRepository.save(message);
		messageDestinationRepository.saveAndFlush(destination);
		if (sendNotification)
		{
			sendFirebaseNotification(message, userAnonymized);
		}
	}

	private boolean mustProcessUnprocessedMessages(User user)
	{
		return !getUnprocessedMessageIds(user).isEmpty();
	}

	private List<Long> getUnprocessedMessageIds(User user)
	{
		MessageDestination anonymousMessageDestination = getAnonymousMessageSource(user).getDestination();
		return messageRepository.findUnprocessedMessagesFromDestination(anonymousMessageDestination);
	}

	public List<Message> getUnprocessedMessages(User user, Predicate<Message> predicate)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		return getUnprocessedMessageIds(user).stream().map(messageSource::getMessage).filter(predicate).toList();
	}

	public List<Message> getMessagesFromRelatedUserAnonymizedId(User user, UUID relatedUserAnonymizedId)
	{
		return getAnonymousMessageSource(user).getMessagesFromRelatedUserAnonymizedId(relatedUserAnonymizedId);
	}

	private void processUnprocessedMessages(User user)
	{
		List<Long> idsOfUnprocessedMessages = getUnprocessedMessageIds(user);

		MessageActionDto emptyPayload = new MessageActionDto(Collections.emptyMap());
		for (long id : idsOfUnprocessedMessages)
		{
			handleMessageAction(user, id, "process", emptyPayload);
		}
	}

	@Transactional
	public MessageDto getMessage(User user, long messageId)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		return dtoManager.createInstance(user, messageSource.getMessage(messageId));
	}

	@Transactional
	public MessageActionDto handleMessageAction(UUID userId, long messageId, String action, MessageActionDto requestPayload)
	{
		User userEntity = userService.lockUserForUpdate(userId);
		return handleMessageAction(userEntity, messageId, action, requestPayload);
	}

	public MessageActionDto handleMessageAction(User userEntity, long messageId, String action, MessageActionDto requestPayload)
	{
		MessageSource messageSource = getAnonymousMessageSource(userEntity);
		return dtoManager.handleAction(userEntity, messageSource.getMessage(messageId), action, requestPayload);
	}

	@Transactional
	public MessageActionDto deleteMessage(User user, long id)
	{
		MessageSource messageSource = getAnonymousMessageSource(user);
		Message message = messageSource.getMessage(id);

		deleteMessage(user, message);

		return MessageActionDto.createInstanceActionDone();
	}

	private void deleteMessage(User user, Message message)
	{
		MessageDto messageDto = dtoManager.createInstance(user, message);
		Require.that(messageDto.canBeDeleted(), InvalidMessageActionException::unprocessedMessageCannotBeDeleted);

		deleteMessages(Collections.singleton(message));
	}

	public void deleteMessages(Collection<Message> messages)
	{
		Set<Message> messagesToBeDeleted = messages.stream().flatMap(m -> m.getMessagesToBeCascadinglyDeleted().stream())
				.collect(Collectors.toSet());
		messagesToBeDeleted.addAll(messages);
		Set<MessageDestination> involvedMessageDestinations = messagesToBeDeleted.stream().map(Message::getMessageDestination)
				.collect(Collectors.toSet());

		messagesToBeDeleted.forEach(Message::prepareForDelete);
		involvedMessageDestinations.forEach(d -> messageDestinationRepository.saveAndFlush(d));

		messagesToBeDeleted.forEach(m -> m.getMessageDestination().remove(m));
		involvedMessageDestinations.forEach(d -> messageDestinationRepository.save(d));
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

	@Transactional
	public MessageSource getMessageSource(UUID id)
	{
		return messageSourceRepository.findById(id)
				.orElseThrow(() -> InvalidDataException.missingEntity(MessageSource.class, id));
	}

	@Transactional
	public MessageDestination getMessageDestination(UUID id)
	{
		return messageDestinationRepository.findById(id)
				.orElseThrow(() -> InvalidDataException.missingEntity(MessageDestination.class, id));
	}

	private Page<MessageDto> wrapMessagesAsDtos(User user, Page<? extends Message> messageEntities, Pageable pageable)
	{
		List<MessageDto> allMessagePayloads = wrapMessagesAsDtos(user, messageEntities.getContent());
		return new PageImpl<>(allMessagePayloads, pageable, messageEntities.getTotalElements());
	}

	private List<MessageDto> wrapMessagesAsDtos(User user, List<? extends Message> messageEntities)
	{
		return messageEntities.stream().map(m -> messageToDto(user, m)).toList();
	}

	public MessageDto messageToDto(User user, Message message)
	{
		return dtoManager.createInstance(user, message);
	}

	public static interface DtoManager
	{
		MessageDto createInstance(User actingUser, Message messageEntity);

		MessageActionDto handleAction(User actingUser, Message messageEntity, String action, MessageActionDto requestPayload);
	}

	@Component
	public static class TheDtoManager implements DtoManager
	{
		private final Map<Class<? extends Message>, DtoManager> managers = new HashMap<>();

		@Override
		public MessageDto createInstance(User user, Message messageEntity)
		{
			return getManager(messageEntity).createInstance(user, messageEntity);
		}

		@Override
		public MessageActionDto handleAction(User user, Message messageEntity, String action, MessageActionDto requestPayload)
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
	public void sendDirectMessageAndFlushToDatabase(Message message, User toUser)
	{
		sendDirectMessage(message, toUser);

		MessageDestination destinationEntity = toUser.getNamedMessageDestination();
		messageDestinationRepository.saveAndFlush(destinationEntity);
	}

	@Transactional
	public void sendMessage(Message message, UserAnonymizedDto toUser)
	{
		MessageDestination destinationEntity = getMessageDestination(toUser.getAnonymousDestination().getId());

		sendMessage(toUser, message, destinationEntity, true);
	}

	@Transactional
	public void sendMessageWithoutNotification(Message message, UserAnonymizedDto toUser)
	{
		MessageDestination destinationEntity = getMessageDestination(toUser.getAnonymousDestination().getId());

		sendMessage(toUser, message, destinationEntity, false);
	}

	public void sendFirebaseNotification(Message message, UserAnonymizedDto toUser)
	{
		toUser.getDevicesAnonymized().stream().filter(da -> da.getFirebaseInstanceId().isPresent())
				.forEach(deviceAnonymized -> this.sendFirebaseNotification(deviceAnonymized, message));
	}

	private void sendFirebaseNotification(DeviceAnonymizedDto deviceAnonymized, Message message)
	{
		LocaleContextHelper.inLocaleContext(
				() -> firebaseService.sendMessage(deviceAnonymized.getId(), deviceAnonymized.getFirebaseInstanceId().get(),
						message), deviceAnonymized.getLocale());
	}

	@Transactional
	public void sendDirectMessage(Message message, User toUser)
	{
		MessageDestination destinationEntity = toUser.getNamedMessageDestination();

		destinationEntity.send(message);

		if (!toUser.isCreatedOnBuddyRequest()) // Do not send SMS before buddy invitation via email/SMS has been accepted
		{
			smsService.send(toUser.getMobileNumber(), SmsTemplate.DIRECT_MESSAGE_NOTIFICATION, Collections.emptyMap());
		}
	}

	@Transactional
	public void removeMessagesFromUser(MessageDestinationDto destination, UUID sentByUserAnonymizedId)
	{
		if (sentByUserAnonymizedId == null)
		{
			throw new IllegalArgumentException("sentByUserAnonymizedId cannot be null");
		}

		MessageDestination destinationEntity = getMessageDestination(destination.getId());
		deleteMessages(destinationEntity.getMessagesFromUser(sentByUserAnonymizedId));
	}

	@Transactional
	public void broadcastMessageToBuddies(UserAnonymizedDto userAnonymized, Supplier<Message> messageSupplier)
	{
		buddyService.getBuddyUsersAnonymized(userAnonymized)
				.forEach(buddyUserAnonymized -> sendMessage(messageSupplier.get(), buddyUserAnonymized));
	}

	@Transactional
	public Page<MessageDto> getActivityRelatedMessages(UUID userId, IntervalActivity intervalActivityEntity, Pageable pageable)
	{
		User user = userService.getValidatedUserEntity(userId);
		MessageSource messageSource = getAnonymousMessageSource(user);
		return wrapMessagesAsDtos(user, messageSource.getActivityRelatedMessages(intervalActivityEntity, pageable), pageable);
	}

	public void deleteMessagesForIntervalActivities(Collection<IntervalActivity> intervalActivities)
	{
		if (intervalActivities.isEmpty())
		{
			return;
		}
		deleteMessages(messageRepository.findByIntervalActivity(intervalActivities));
	}
}
