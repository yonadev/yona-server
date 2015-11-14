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
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.entities.Buddy.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.BuddyConnectResponseMessage;

@JsonRootName("buddyConnectRequestMessage")
public class BuddyConnectRequestMessageDTO extends MessageDTO {
	private static final String ACCEPT = "accept";
	private UserDTO requestingUser;
	private final String message;
	private Set<String> goals;
	private boolean isAccepted;

	private BuddyConnectRequestMessageDTO(BuddyConnectRequestMessage buddyConnectRequestMessageEntity, UUID id,
			UserDTO requestingUser, UUID accessorID, String nickname, String message, Set<String> goals,
			boolean isAccepted) {
		super(id);
		if (buddyConnectRequestMessageEntity == null) {
			throw new IllegalArgumentException("buddyConnectRequestMessageEntity cannot be null");
		}
		if (requestingUser == null) {
			throw new IllegalArgumentException("requestingUser cannot be null");
		}
		if (accessorID == null) {
			throw new IllegalArgumentException("accessorID cannot be null");
		}
		this.requestingUser = requestingUser;
		this.message = message;
		this.goals = goals;
		this.isAccepted = isAccepted;
	}

	@Override
	public Set<String> getPossibleActions() {
		Set<String> possibleActions = new HashSet<>();
		if (!isAccepted) {
			possibleActions.add(ACCEPT);
		}
		return possibleActions;
	}

	public UserDTO getRequestingUser() {
		return requestingUser;
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

	public static BuddyConnectRequestMessageDTO createInstance(UserDTO requestingUser,
			BuddyConnectRequestMessage messageEntity) {
		return new BuddyConnectRequestMessageDTO(messageEntity, messageEntity.getID(),
				UserDTO.createInstance(messageEntity.getRequestingUser()), messageEntity.getAccessorID(),
				messageEntity.getNickname(), messageEntity.getMessage(),
				messageEntity.getGoals().stream().map(g -> g.getName()).collect(Collectors.toSet()),
				messageEntity.isAccepted());
	}

	@Component
	private static class Factory implements DTOManager {
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@Autowired
		private UserService userService;

		@PostConstruct
		private void init() {
			theDTOFactory.addManager(BuddyConnectRequestMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity) {
			return BuddyConnectRequestMessageDTO.createInstance(actingUser, (BuddyConnectRequestMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload) {
			switch (action) {
			case ACCEPT:
				return handleAction_Accept(actingUser, (BuddyConnectRequestMessage) messageEntity, requestPayload);
			default:
				throw new IllegalArgumentException("Action '" + action + "' is not supported");
			}
		}

		private MessageActionDTO handleAction_Accept(UserDTO acceptingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, MessageActionDTO payload) {

			BuddyDTO buddy = buddyService.addBuddyToAcceptingUser(
					connectRequestMessageEntity.getRequestingUser().getID(), connectRequestMessageEntity.getNickname(),
					connectRequestMessageEntity.getGoals(), connectRequestMessageEntity.getAccessorID());

			userService.addBuddy(acceptingUser, buddy);

			updateMessageStatusAsAccepted(connectRequestMessageEntity);

			sendResponseMessageToRequestingUser(acceptingUser, connectRequestMessageEntity,
					payload.getProperty("message"));

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private void updateMessageStatusAsAccepted(BuddyConnectRequestMessage connectRequestMessageEntity) {
			connectRequestMessageEntity.setStatus(Status.ACCEPTED);
			Message.getRepository().save(connectRequestMessageEntity);
		}

		private void sendResponseMessageToRequestingUser(UserDTO acceptingUser,
				BuddyConnectRequestMessage connectRequestMessageEntity, String responseMessage) {
			MessageDestination messageDestination = connectRequestMessageEntity.getRequestingUser()
					.getNamedMessageDestination();
			assert messageDestination != null;
			messageDestination.send(BuddyConnectResponseMessage.createInstance(acceptingUser.getID(),
					acceptingUser.getPrivateData().getVpnProfile().getLoginID(),
					acceptingUser.getPrivateData().getAnonymousMessageDestinationID(), responseMessage,
					connectRequestMessageEntity.getBuddyID(), Status.ACCEPTED));
			MessageDestination.getRepository().save(messageDestination);
		}
	}
}
