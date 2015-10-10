/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOFactory;
import nu.yona.server.messaging.service.MessageService.TheDTOFactory;
import nu.yona.server.rest.BadRequestException;
import nu.yona.server.subscriptions.entities.Accessor;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("buddyConnectResponseMessage")
public class BuddyConnectResponseMessageDTO extends MessageDTO {
	private static final String PROCESS = "process";
	private UserDTO respondingUserResource;
	private final String message;
	private boolean isProcessed;
	private BuddyConnectResponseMessage messageEntity;

	private BuddyConnectResponseMessageDTO(BuddyConnectResponseMessage buddyConnectResponseMessageEntity, UUID id,
			UserDTO respondingUserResource, String message, boolean isProcessed) {
		super(id);
		this.messageEntity = buddyConnectResponseMessageEntity;
		this.respondingUserResource = respondingUserResource;
		this.message = message;
		this.isProcessed = isProcessed;
	}

	public UserDTO getRespondingUser() {
		return respondingUserResource;
	}

	public String getMessage() {
		return message;
	}

	public boolean isProcessed() {
		return isProcessed;
	}

	public static BuddyConnectResponseMessageDTO createInstance(User actingUserEntity,
			BuddyConnectResponseMessage messageEntity) {
		return new BuddyConnectResponseMessageDTO(messageEntity, messageEntity.getID(),
				UserDTO.createInstance(messageEntity.getRespondingUser()),
				messageEntity.getMessage(), messageEntity.isProcessed());
	}

	@Override
	public Set<String> getPossibleActions() {
		Set<String> possibleActions = new HashSet<>();
		if (!isProcessed) {
			possibleActions.add(PROCESS);
		}
		return possibleActions;
	}

	@Override
	public MessageActionDTO handleAction(User requestingUserEntity, String action, MessageActionDTO payload) {
		switch (action) {
		case PROCESS:
			return handleAction_Process(requestingUserEntity);
		default:
			throw new BadRequestException("Action '" + action + "' is not supported");
		}
	}

	private MessageActionDTO handleAction_Process(User requestingUserEntity) {
		updateBuddyWithSecretUserInfo();
		addDestinationOfBuddyToAccessor(requestingUserEntity.getAccessorID(), messageEntity.getDestinationID());
		updateMessageStatusAsProcessed();

		return new MessageActionDTO(Collections.singletonMap("status", "done"));
	}

	private void updateBuddyWithSecretUserInfo() {
		Buddy buddy = Buddy.getRepository().findOne(messageEntity.getBuddyID());
		buddy.setAccessorID(messageEntity.getAccessorID());
		buddy.setDestinationID(messageEntity.getDestinationID());
		Buddy.getRepository().save(buddy);
	}

	private void addDestinationOfBuddyToAccessor(UUID accessorID, UUID destinationID) {
		Accessor accessor = Accessor.getRepository().findOne(accessorID);
		accessor.addDestination(MessageDestination.getRepository().findOne(destinationID));
		Accessor.getRepository().save(accessor);
	}

	private void updateMessageStatusAsProcessed() {
		messageEntity.setProcessed();
		Message.getRepository().save(messageEntity);
	}

	@Component
	private static class Factory implements DTOFactory {
		@Autowired
		private TheDTOFactory theDTOFactory;

		@PostConstruct
		private void init() {
			theDTOFactory.addFactory(BuddyConnectResponseMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(User actingUserEntity, Message messageEntity) {
			return BuddyConnectResponseMessageDTO.createInstance(actingUserEntity,
					(BuddyConnectResponseMessage) messageEntity);
		}
	}
}
