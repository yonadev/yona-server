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

		User actingUserEntity = userService.getEntityByID(userID);
		MessageSource messageSource = actingUserEntity.getNamedMessageSource();
		return wrapAllMessagesAsDTOs(actingUserEntity, messageSource);
	}

	private List<MessageDTO> wrapAllMessagesAsDTOs(User actingUserEntity, MessageSource messageSource) {
		return wrapMessagesAsDTOs(actingUserEntity, messageSource.getAllMessages());
	}

	public MessageActionDTO handleMessageAction(long userID, UUID id, String action, MessageActionDTO requestPayload) {
		User actingUserEntity = userService.getEntityByID(userID);
		MessageSource messageSource = actingUserEntity.getNamedMessageSource();
		MessageActionDTO responsePayload = dtoFactory.createInstance(actingUserEntity, messageSource.getMessage(id))
				.handleAction(actingUserEntity, action, requestPayload);
		return responsePayload;
	}

	public List<MessageDTO> getAnonymousMessages(long userID) {

		User actingUserEntity = userService.getEntityByID(userID);
		MessageSource messageSource = actingUserEntity.getAnonymousMessageSource();
		return wrapAllMessagesAsDTOs(actingUserEntity, messageSource);
	}

	private List<MessageDTO> wrapMessagesAsDTOs(User actingUserEntity, List<Message> messageEntities) {
		List<MessageDTO> allMessagePayloads = messageEntities.stream()
				.map(m -> dtoFactory.createInstance(actingUserEntity, m)).collect(Collectors.toList());
		return allMessagePayloads;
	}

	public static interface DTOFactory {
		MessageDTO createInstance(User actingUserEntity, Message messageEntity);
	}

	@Component
	public static class TheDTOFactory implements DTOFactory {
		private Map<Class<? extends Message>, DTOFactory> factories = new HashMap<>();

		@Override
		public MessageDTO createInstance(User actingUserEntity, Message messageEntity) {
			assert messageEntity != null;

			for (Class<? extends Message> dtoClass : factories.keySet()) {
				if (dtoClass.isInstance(messageEntity)) {
					return factories.get(dtoClass).createInstance(actingUserEntity, messageEntity);
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
