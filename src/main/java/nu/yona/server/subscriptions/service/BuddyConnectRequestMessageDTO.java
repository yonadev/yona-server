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
import java.util.stream.Collectors;

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
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.Buddy.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("buddyConnectRequestMessage")
public class BuddyConnectRequestMessageDTO extends MessageDTO {
	private static final String ACCEPT = "accept";
	private UserDTO requestingUserResource;
	private final String message;
	private Set<String> goals;
	private boolean isAccepted;
	private String nickname;
	private UUID accessorID;
	private BuddyConnectRequestMessage messageEntity;

	private BuddyConnectRequestMessageDTO(BuddyConnectRequestMessage buddyConnectRequestMessageEntity, UUID id,
			UserDTO requestingUserResource, UUID accessorID, String nickname, String message, Set<String> goals,
			boolean isAccepted) {
		super(id);
		if (buddyConnectRequestMessageEntity == null) {
			throw new IllegalArgumentException("buddyConnectRequestMessageEntity cannot be null");
		}
		if (requestingUserResource == null) {
			throw new IllegalArgumentException("requestingUserResource cannot be null");
		}
		if (accessorID == null) {
			throw new IllegalArgumentException("accessorID cannot be null");
		}
		this.messageEntity = buddyConnectRequestMessageEntity;
		this.requestingUserResource = requestingUserResource;
		this.accessorID = accessorID;
		this.nickname = nickname;
		this.message = message;
		this.goals = goals;
		this.isAccepted = isAccepted;
	}

	public UserDTO getRequestingUser() {
		return requestingUserResource;
	}

	public String getMessage() {
		return message;
	}

	public Set<String> getGoals() {
		return Collections.unmodifiableSet(goals);
	}

	public boolean isAccepted() {
		return isAccepted;
	}

	public static BuddyConnectRequestMessageDTO createInstance(User actingUserEntity,
			BuddyConnectRequestMessage messageEntity) {
		return new BuddyConnectRequestMessageDTO(messageEntity, messageEntity.getID(),
				UserDTO.createMinimallyInitializedInstance(messageEntity.getRequestingUser()),
				messageEntity.getAccessorID(), messageEntity.getNickname(), messageEntity.getMessage(),
				messageEntity.getGoals().stream().map(g -> g.getName()).collect(Collectors.toSet()),
				messageEntity.isAccepted());
	}

	@Override
	public Set<String> getPossibleActions() {
		Set<String> possibleActions = new HashSet<>();
		if (!isAccepted) {
			possibleActions.add(ACCEPT);
		}
		return possibleActions;
	}

	@Override
	public MessageActionDTO handleAction(User respondingUserEntity, String action, MessageActionDTO payload) {
		switch (action) {
		case ACCEPT:
			return handleAction_Accept(respondingUserEntity, payload);
		default:
			throw new BadRequestException("Action '" + action + "' is not supported");
		}
	}

	private MessageActionDTO handleAction_Accept(User respondingUserEntity, MessageActionDTO payload) {
		Buddy buddy = createBuddyForRespondingUser(respondingUserEntity);
		messageEntity.setStatus(Status.ACCEPTED);
		sendResponseMessageToRequestingUser(respondingUserEntity, payload.getProperty("message"));

		respondingUserEntity.addBuddy(Buddy.getRepository().save(buddy));
		User.getRepository().save(respondingUserEntity);
		Message.getRepository().save(messageEntity);

		return new MessageActionDTO(Collections.singletonMap("status", "done"));
	}

	private void sendResponseMessageToRequestingUser(User respondingUserEntity, String responseMessage) {
		MessageDestination messageDestination = User.getRepository().findOne(requestingUserResource.getID())
				.getNamedMessageDestination();
		messageDestination.send(BuddyConnectResponseMessage.createInstance(respondingUserEntity,
				respondingUserEntity.getAnonymousMessageDestination().getID(), responseMessage,
				messageEntity.getBuddyID(), Status.ACCEPTED));
		MessageDestination.getRepository().save(messageDestination);
	}

	private Buddy createBuddyForRespondingUser(User respondingUserEntity) {
		Buddy buddy = Buddy.createInstance(respondingUserEntity, nickname);
		buddy.setGoalNames(goals);
		buddy.setAccessorID(accessorID);
		buddy.setReceivingStatus(Status.ACCEPTED);
		return buddy;
	}

	@Component
	private static class Factory implements DTOFactory {
		@Autowired
		private TheDTOFactory theDTOFactory;

		@PostConstruct
		private void init() {
			theDTOFactory.addFactory(BuddyConnectRequestMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(User actingUserEntity, Message messageEntity) {
			return BuddyConnectRequestMessageDTO.createInstance(actingUserEntity,
					(BuddyConnectRequestMessage) messageEntity);
		}
	}
}
