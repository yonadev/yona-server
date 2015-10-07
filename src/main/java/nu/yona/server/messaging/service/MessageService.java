/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.UserService;

@Service
public class MessageService {
	@Autowired
	private UserService userService;

	@Autowired
	private TheDTOFactory dtoFactory;

	public List<MessageDTO> getDirectMessages(long userID) {

		User userEntity = userService.getEntityByID(userID);
		MessageSource messageSource = userEntity.getNamedMessageSource();
		return wrapAllMessagesAsDTOs(userEntity, messageSource);
	}

	public MessageDTO getDirectMessage(long userID, UUID messageID) {
		User userEntity = userService.getEntityByID(userID);
		MessageSource messageSource = userEntity.getNamedMessageSource();
		return dtoFactory.createInstance(userEntity, messageSource.getMessage(messageID));
	}

	private List<MessageDTO> wrapAllMessagesAsDTOs(User userEntity, MessageSource messageSource) {
		return wrapMessagesAsDTOs(userEntity, messageSource.getAllMessages());
	}

	public MessageActionDTO handleMessageAction(long userID, UUID id, String action, MessageActionDTO requestPayload) {
		User userEntity = userService.getEntityByID(userID);
		MessageSource messageSource = userEntity.getNamedMessageSource();
		MessageActionDTO responsePayload = dtoFactory.createInstance(userEntity, messageSource.getMessage(id))
				.handleAction(userEntity, action, requestPayload);
		return responsePayload;
	}

	public List<MessageDTO> getAnonymousMessages(long userID) {

		User userEntity = userService.getEntityByID(userID);
		MessageSource messageSource = userEntity.getAnonymousMessageSource();
		return wrapAllMessagesAsDTOs(userEntity, messageSource);
	}

	public MessageDTO getAnonymousMessage(long userID, UUID messageID) {
		User userEntity = userService.getEntityByID(userID);
		MessageSource messageSource = userEntity.getAnonymousMessageSource();
		return dtoFactory.createInstance(userEntity, messageSource.getMessage(messageID));
	}

	private List<MessageDTO> wrapMessagesAsDTOs(User userEntity, List<Message> messageEntities) {
		List<MessageDTO> allMessagePayloads = messageEntities.stream()
				.map(m -> dtoFactory.createInstance(userEntity, m)).collect(Collectors.toList());
		return allMessagePayloads;
	}

	public static interface DTOFactory {
		MessageDTO createInstance(User userEntity, Message messageEntity);
	}

	@Component
	public static class TheDTOFactory implements DTOFactory {
		private Map<Class<? extends Message>, DTOFactory> factories = new HashMap<>();

		@Override
		public MessageDTO createInstance(User userEntity, Message messageEntity) {
			assert messageEntity != null;

			for (Class<? extends Message> dtoClass : factories.keySet()) {
				if (dtoClass.isInstance(messageEntity)) {
					return factories.get(dtoClass).createInstance(userEntity, messageEntity);
				}
			}
			throw new IllegalArgumentException("No DTO factory registered for class " + messageEntity.getClass());
		}

		public void addFactory(Class<? extends Message> messageEntityClass, DTOFactory factory) {
			DTOFactory previousFactory = factories.put(messageEntityClass, factory);
			assert previousFactory == null;
		}
	}
}
