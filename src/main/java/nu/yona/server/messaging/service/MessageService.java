/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import nu.yona.server.analysis.model.GoalConflictMessage;
import nu.yona.server.analysis.service.GoalConflictMessageDTO;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyConnectRequestMessageDTO;
import nu.yona.server.subscriptions.service.BuddyConnectResponseMessageDTO;
import nu.yona.server.subscriptions.service.UserService;

@Component
public class MessageService {
	@Autowired
	UserService userService;

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
		MessageActionDTO responsePayload = createDTOInstance(actingUserEntity, messageSource.getMessage(id))
				.handleAction(actingUserEntity, action, requestPayload);
		return responsePayload;
	}

	public List<MessageDTO> getAnonymousMessages(long userID) {

		User actingUserEntity = userService.getEntityByID(userID);
		MessageSource messageSource = actingUserEntity.getAnonymousMessageSource();
		return wrapAllMessagesAsDTOs(actingUserEntity, messageSource);
	}

	private List<MessageDTO> wrapMessagesAsDTOs(User actingUserEntity, List<Message> messageEntities) {
		List<MessageDTO> allMessagePayloads = messageEntities.stream().map(m -> createDTOInstance(actingUserEntity, m))
				.collect(Collectors.toList());
		return allMessagePayloads;
	}

	private MessageDTO createDTOInstance(User actingUserEntity, Message messageEntity) {

		if (messageEntity instanceof BuddyConnectRequestMessage) {
			return BuddyConnectRequestMessageDTO.createInstance(actingUserEntity,
					(BuddyConnectRequestMessage) messageEntity);
		}
		if (messageEntity instanceof BuddyConnectResponseMessage) {
			return BuddyConnectResponseMessageDTO.createInstance(actingUserEntity,
					(BuddyConnectResponseMessage) messageEntity);
		}
		if (messageEntity instanceof GoalConflictMessage) {
			return GoalConflictMessageDTO.createInstance(actingUserEntity, (GoalConflictMessage) messageEntity);
		}

		throw new IllegalStateException("Unknown message type");
	}
}
