/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.Buddy.Status;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.rest.UserController;

@Component
public class BuddyService {
	@Autowired
	private UserService userService;

	public BuddyDTO getBuddy(Optional<String> password, long idOfRequestingUser, long buddyID) {
		return BuddyDTO.createInstance(getEntityByID(buddyID));
	}

	public BuddyDTO addBuddy(Optional<String> password, long idOfRequestingUser, BuddyDTO buddyResource) {
		User requestingUserEntity = userService.getEntityByID(idOfRequestingUser);
		User buddyUserEntity = getBuddyUser(buddyResource);
		Buddy newBuddyEntity;
		if (buddyUserEntity == null) {
			newBuddyEntity = handleBuddyRequestForNewUser(requestingUserEntity, buddyResource);
		} else {
			newBuddyEntity = handleBuddyRequestForExistingUser(requestingUserEntity, buddyResource, buddyUserEntity);
		}
		return BuddyDTO.createInstance(newBuddyEntity);
	}

	private Buddy handleBuddyRequestForNewUser(User requestingUserEntity, BuddyDTO buddyResource) {
		UserDTO buddyUserResource = buddyResource.getUser();
		User buddyUserEntity = User.createInstanceOnBuddyRequest(buddyUserResource.getFirstName(),
				buddyUserResource.getLastName(), buddyUserResource.getNickName(), buddyUserResource.getEmailAddress(),
				buddyUserResource.getMobileNumber());
		User savedBuddyUserEntity = User.getRepository().save(buddyUserEntity);
		sendInvitationMessage(savedBuddyUserEntity, buddyResource);
		return handleBuddyRequestForExistingUser(requestingUserEntity, buddyResource, buddyUserEntity);
	}

	private void sendInvitationMessage(User buddyUserEntity, BuddyDTO buddyResource) {
		String userURL = UserController.getUserLink(buddyUserEntity.getID(), false).getHref();
		System.out.println(buddyResource.getMessage());
		System.out.println("\nTo accept this request, install the Yona app");
		System.out.println("\nTo mimic the Yona app, post the appropriate message to this URL: " + userURL);
	}

	private Buddy handleBuddyRequestForExistingUser(User requestingUserEntity, BuddyDTO buddyResource,
			User buddyUserEntity) {
		Buddy buddyEntity = buddyResource.createBuddyEntity(buddyUserEntity);
		buddyEntity.setSendingStatus(Status.REQUESTED);
		Buddy savedBuddyEntity = Buddy.getRepository().save(buddyEntity);
		requestingUserEntity.addBuddy(savedBuddyEntity);

		MessageDestination messageDestination = buddyUserEntity.getNamedMessageDestination();
		messageDestination.send(
				BuddyConnectRequestMessage.createInstance(requestingUserEntity, requestingUserEntity.getNickName(),
						buddyResource.getMessage(), requestingUserEntity.getGoals(), savedBuddyEntity.getID()));
		MessageDestination.getRepository().save(messageDestination);
		User.getRepository().save(requestingUserEntity);

		return savedBuddyEntity;
	}

	private Buddy getEntityByID(long id) {
		Buddy entity = Buddy.getRepository().findOne(id);
		if (entity == null) {
			throw new BuddyNotFoundException(id);
		}
		return entity;
	}

	private User getBuddyUser(BuddyDTO buddyResource) {
		try {
			return UserService.findUserByEmailAddressOrMobileNumber(buddyResource.getUser().getEmailAddress(),
					buddyResource.getUser().getMobileNumber());
		} catch (UserNotFoundException e) {
			return null;
		}
	}
}
