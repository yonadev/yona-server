/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyConnectRequestMessage;
import nu.yona.server.subscriptions.entities.User;

@Service
@Transactional
public class BuddyService
{
	@Autowired
	private UserService userService;

	public BuddyDTO getBuddy(UUID buddyID)
	{
		return BuddyDTO.createInstance(getEntityByID(buddyID));
	}

	public Set<BuddyDTO> getBuddiesOfUser(UUID forUserID)
	{
		UserDTO user = userService.getPrivateUser(forUserID);
		return getBuddies(user.getPrivateData().getBuddyIDs());
	}

	public BuddyDTO addBuddyToRequestingUser(UUID idOfRequestingUser, BuddyDTO buddy)
	{
		UserDTO requestingUser = userService.getPrivateUser(idOfRequestingUser);
		User buddyUserEntity = getBuddyUser(buddy);
		BuddyDTO newBuddyEntity;
		if (buddyUserEntity == null)
		{
			newBuddyEntity = handleBuddyRequestForNewUser(requestingUser, buddy);
		}
		else
		{
			newBuddyEntity = handleBuddyRequestForExistingUser(requestingUser, buddy, buddyUserEntity);
		}
		return newBuddyEntity;
	}

	public BuddyDTO addBuddyToAcceptingUser(UUID buddyUserID, String buddyNickName, Set<Goal> buddyGoals, UUID buddyLoginID)
	{
		Buddy buddy = Buddy.createInstance(buddyUserID, buddyNickName);
		buddy.setGoals(buddyGoals);
		buddy.setReceivingStatus(BuddyAnonymized.Status.ACCEPTED);
		buddy.setLoginID(buddyLoginID);

		return BuddyDTO.createInstance(Buddy.getRepository().save(buddy));
	}

	private BuddyDTO handleBuddyRequestForNewUser(UserDTO requestingUser, BuddyDTO buddy)
	{
		UserDTO buddyUser = buddy.getUser();
		User buddyUserEntity = User.createInstanceOnBuddyRequest(buddyUser.getFirstName(), buddyUser.getLastName(),
				buddyUser.getPrivateData().getNickName(), buddyUser.getMobileNumber());
		User savedBuddyUserEntity = User.getRepository().save(buddyUserEntity);
		sendInvitationMessage(savedBuddyUserEntity, buddy);
		return handleBuddyRequestForExistingUser(requestingUser, buddy, buddyUserEntity);
	}

	private void sendInvitationMessage(User buddyUserEntity, BuddyDTO buddy)
	{
		/*
		 * String userURL = UserController.getUserLink(buddyUserEntity.getID(), false).getHref();
		 * System.out.println(buddy.getMessage()); System.out.println("\nTo accept this request, install the Yona app");
		 * System.out.println( "\nTo mimic the Yona app, post the appropriate message to this URL: " + userURL);
		 */
	}

	private BuddyDTO handleBuddyRequestForExistingUser(UserDTO requestingUser, BuddyDTO buddy, User buddyUserEntity)
	{
		buddy.getUser().setUserID(buddyUserEntity.getID());
		Buddy buddyEntity = buddy.createBuddyEntity();
		buddyEntity.setSendingStatus(BuddyAnonymized.Status.REQUESTED);
		Buddy savedBuddyEntity = Buddy.getRepository().save(buddyEntity);
		BuddyDTO savedBuddy = BuddyDTO.createInstance(savedBuddyEntity);
		userService.addBuddy(requestingUser, savedBuddy);

		MessageDestination messageDestination = buddyUserEntity.getNamedMessageDestination();
		messageDestination.send(BuddyConnectRequestMessage.createInstance(requestingUser.getID(),
				requestingUser.getPrivateData().getVpnProfile().getLoginID(), requestingUser.getPrivateData().getGoals(),
				requestingUser.getPrivateData().getNickName(), buddy.getMessage(), savedBuddyEntity.getID()));
		MessageDestination.getRepository().save(messageDestination);

		return savedBuddy;
	}

	private Buddy getEntityByID(UUID id)
	{
		Buddy entity = Buddy.getRepository().findOne(id);
		if (entity == null)
		{
			throw new BuddyNotFoundException(id);
		}
		return entity;
	}

	private User getBuddyUser(BuddyDTO buddy)
	{
		try
		{
			return UserService.findUserByMobileNumber(buddy.getUser().getMobileNumber());
		}
		catch (UserNotFoundException e)
		{
			return null;
		}
	}

	public void updateBuddyWithSecretUserInfo(UUID buddyID, UUID loginID)
	{
		Buddy buddy = Buddy.getRepository().findOne(buddyID);
		buddy.setLoginID(loginID);
	}

	public Set<BuddyDTO> getBuddies(Set<UUID> buddyIDs)
	{
		return buddyIDs.stream().map(id -> getBuddy(id)).collect(Collectors.toSet());
	}
}
